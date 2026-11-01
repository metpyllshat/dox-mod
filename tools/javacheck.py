"""Статическая проверка Java-кода мода без компилятора.

В песочнице нет javac, поэтому ловим три класса ошибок, на которых сборка
падает чаще всего:

1. синтаксис — через tree-sitter-java (настоящая грамматика Java 17);
2. ссылки на чужие классы нашего мода (`Sync.full`, `MatchData.get`), которых нет;
3. локальные переменные, которые затеняют пакет (`NetData net = ...` ломает
   `net.minecraft...` — ровно та ошибка, что была в Minigame);
4. использование класса нашего мода без импорта (`TitleScreen` в ClientHooks);
5. импорты несуществующих классов мода;
6. вызовы Font.draw/drawShadow с PoseStack — таких перегрузок в 1.20.1 нет,
   текст рисуется только через GuiGraphics.drawString.

Запуск: python3 tools/javacheck.py
"""
import glob
import os
import sys

from tree_sitter import Language, Parser
import tree_sitter_java

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "blackchannel", "src", "main", "java")
PKG_PREFIXES = {"net", "java", "javax", "com", "org", "dev", "io"}

parser = Parser(Language(tree_sitter_java.language()))


def walk(node):
    yield node
    for child in node.children:
        yield from walk(child)


def text(src, node):
    return src[node.start_byte:node.end_byte].decode("utf-8", "replace")


def param_count(params_node, src):
    """Сколько аргументов принимает метод: varargs считаем «сколько угодно от k»."""
    if params_node is None:
        return 0, False
    count = 0
    varargs = False
    for child in params_node.children:
        if child.type in ("formal_parameter", "spread_parameter", "receiver_parameter"):
            count += 1
            if child.type == "spread_parameter":
                varargs = True
    # receiver_parameter — это this/super в записи, он не аргумент вызова
    for child in params_node.children:
        if child.type == "receiver_parameter":
            count -= 1
    return count, varargs


ARITY = {}


def collect_members(src, root, members):
    """Собирает поля и методы всех классов файла: {class: {member, ...}}."""
    for node in walk(root):
        if node.type not in ("class_declaration", "interface_declaration", "record_declaration", "enum_declaration"):
            continue
        name_node = node.child_by_field_name("name")
        if name_node is None:
            continue
        cls = text(src, name_node)
        # вложенный класс виден снаружи как член родителя: NodeBlock.Kind
        owner = node.parent
        while owner is not None and owner.type != "class_body":
            owner = owner.parent
        if owner is not None and owner.parent is not None:
            outer = owner.parent.child_by_field_name("name")
            if outer is not None:
                members.setdefault(text(src, outer), set()).add(cls)
        body = node.child_by_field_name("body")
        if body is None:
            continue
        bucket = members.setdefault(cls, set())
        # Компоненты записи (record Entry(String id, ...)) — тоже члены класса
        if node.type == "record_declaration":
            params = node.child_by_field_name("parameters")
            if params is not None:
                for param in params.children:
                    if param.type != "formal_parameter":
                        continue
                    ident = param.child_by_field_name("name")
                    if ident is not None:
                        bucket.add(text(src, ident))
        for member in body.children:
            if member.type in ("field_declaration", "constant_declaration"):
                for sub in walk(member):
                    if sub.type == "variable_declarator":
                        ident = sub.child_by_field_name("name")
                        if ident is not None:
                            bucket.add(text(src, ident))
            elif member.type in ("method_declaration", "constructor_declaration", "compact_constructor_declaration"):
                ident = member.child_by_field_name("name")
                if ident is not None:
                    bucket.add(text(src, ident))
                    if member.type == "method_declaration":
                        count, varargs = param_count(member.child_by_field_name("parameters"), src)
                        ARITY.setdefault(cls, {}).setdefault(text(src, ident), set()).add((count, varargs))
            elif member.type == "enum_constant":
                ident = member.child_by_field_name("name")
                if ident is not None:
                    bucket.add(text(src, ident))


def package_of(src, root):
    for node in walk(root):
        if node.type == "package_declaration":
            return text(src, node).replace("package", "").strip().rstrip(";")
    return ""


def simple_imports(src, root):
    """Импортированные имена: {простое имя: полное}, плюс признак wildcard-пакета."""
    single, wild = {}, set()
    for node in walk(root):
        if node.type != "import_declaration":
            continue
        raw = text(src, node).replace("import", "").replace("static", "").strip().rstrip(";")
        if raw.endswith(".*"):
            wild.add(raw[:-2])
        else:
            single[raw.split(".")[-1]] = raw
    return single, wild


def main():
    files = sorted(glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True))
    problems = []
    members = {}
    trees = {}

    for path in files:
        src = open(path, "rb").read()
        tree = parser.parse(src)
        trees[path] = (src, tree)
        if tree.root_node.has_error:
            for node in walk(tree.root_node):
                if node.type == "ERROR" or node.is_missing:
                    line = node.start_point[0] + 1
                    snippet = text(src, node)[:70].replace("\n", " ")
                    problems.append(f"{os.path.relpath(path, ROOT)}:{line}: синтаксис: {snippet}")
        collect_members(src, tree.root_node, members)

    known = set(members)
    packages = {}
    for path, (src, tree) in trees.items():
        pkg = package_of(src, tree.root_node)
        declared = set()
        for node in walk(tree.root_node):
            if node.type in ("class_declaration", "interface_declaration", "record_declaration", "enum_declaration"):
                name_node = node.child_by_field_name("name")
                if name_node is not None:
                    declared.add(text(src, name_node))
        for cls in declared:
            packages.setdefault(cls, set()).add(pkg)

    for path, (src, tree) in trees.items():
        rel = os.path.relpath(path, ROOT)
        # 3. затенение пакетов объявленными именами (переменные, параметры, поля)
        declared = []
        for node in walk(tree.root_node):
            if node.type == "variable_declarator":
                ident = node.child_by_field_name("name")
                if ident is not None:
                    declared.append(ident)
            elif node.type == "formal_parameter":
                for sub in node.children:
                    if sub.type == "identifier":
                        declared.append(sub)
                        break
            elif node.type == "enhanced_for_statement":
                ident = node.child_by_field_name("name")
                if ident is not None:
                    declared.append(ident)
        for ident_node in declared:
            ident = text(src, ident_node)
            if ident in PKG_PREFIXES:
                line = ident_node.start_point[0] + 1
                problems.append(f"{rel}:{line}: имя '{ident}' затеняет пакет")
        # 2. обращения к нашим классам
        for node in walk(tree.root_node):
            if node.type != "method_invocation" and node.type != "field_access":
                continue
            obj = node.child_by_field_name("object")
            # у field_access имя лежит в поле "field", у method_invocation — в "name"
            name = node.child_by_field_name("name") or node.child_by_field_name("field")
            if obj is None or name is None or obj.type != "identifier":
                continue
            cls = text(src, obj)
            if cls not in known:
                continue
            member = text(src, name)
            if member not in members[cls]:
                line = name.start_point[0] + 1
                problems.append(f"{rel}:{line}: у класса {cls} нет '{member}'")

    # 4. импорты наших классов должны существовать
    for path, (src, tree) in trees.items():
        rel = os.path.relpath(path, ROOT)
        for node in walk(tree.root_node):
            if node.type != "import_declaration":
                continue
            raw = text(src, node).replace("import", "").replace("static", "").strip().rstrip(";")
            if not raw.startswith("dev.blackchannel."):
                continue
            cls = raw.split(".")[-1]
            if cls not in known and cls != "*":
                line = node.start_point[0] + 1
                problems.append(f"{rel}:{line}: импорт несуществующего класса {raw}")

    # 4b. вызовы наших методов с неверным числом аргументов (Ui.t с аргументами)
    for path, (src, tree) in trees.items():
        rel = os.path.relpath(path, ROOT)
        for node in walk(tree.root_node):
            if node.type != "method_invocation":
                continue
            obj = node.child_by_field_name("object")
            name = node.child_by_field_name("name")
            args = node.child_by_field_name("arguments")
            if obj is None or name is None or args is None or obj.type != "identifier":
                continue
            cls = text(src, obj)
            method = text(src, name)
            variants = ARITY.get(cls, {}).get(method)
            if not variants:
                continue
            given = sum(1 for c in args.children if c.type not in ("(", ")", ","))
            ok = False
            for count, varargs in variants:
                if varargs:
                    if given >= count - 1:
                        ok = True
                elif given == count:
                    ok = True
            if ok:
                continue
            want = ", ".join(f"{c}{'+' if v else ''}" for c, v in sorted(variants))
            line = name.start_point[0] + 1
            problems.append(f"{rel}:{line}: {cls}.{method} ждёт аргументов {want}, передано {given}")

    # 4c. обращение к полю профиля или другого нашего объекта, которого нет
    for path, (src, tree) in trees.items():
        rel = os.path.relpath(path, ROOT)
        typed = {}
        # Сколько раз имя объявлено в файле: одно имя в разных методах может значить
        # разные типы, и проверять поле по одному из них нельзя
        declared_times = {}
        for node in walk(tree.root_node):
            if node.type in ("variable_declarator", "formal_parameter"):
                ident = node.child_by_field_name("name")
                if ident is not None:
                    declared_times[text(src, ident)] = declared_times.get(text(src, ident), 0) + 1
            elif node.type == "enhanced_for_statement":
                ident = node.child_by_field_name("name")
                if ident is not None:
                    declared_times[text(src, ident)] = declared_times.get(text(src, ident), 0) + 1
        for node in walk(tree.root_node):
            target = None
            if node.type == "variable_declarator":
                t = node.child_by_field_name("type")
                ident = node.child_by_field_name("name")
                if t is not None and ident is not None:
                    target = (t, ident)
            elif node.type == "formal_parameter":
                t = node.child_by_field_name("type")
                ident = node.child_by_field_name("name")
                if t is not None and ident is not None:
                    target = (t, ident)
            elif node.type == "enhanced_for_statement":
                t = node.child_by_field_name("type")
                ident = node.child_by_field_name("name")
                if t is not None and ident is not None:
                    target = (t, ident)
            if target is None:
                continue
            # тип может быть записан через пакет: NetData.Profile -> Profile
            raw_type = text(src, target[0]).replace("<", " ").split()[0] if text(src, target[0]) else ""
            type_name = raw_type.split(".")[-1]
            if type_name in known:
                # одно имя в файле может означать разные типы в разных методах: собираем все
                typed.setdefault(text(src, target[1]), set()).add(type_name)
        if not typed:
            continue
        for node in walk(tree.root_node):
            if node.type != "field_access":
                continue
            obj = node.child_by_field_name("object")
            name = node.child_by_field_name("name") or node.child_by_field_name("field")
            if obj is None or name is None or obj.type != "identifier":
                continue
            classes = typed.get(text(src, obj))
            if not classes or declared_times.get(text(src, obj), 0) > 1:
                continue
            member = text(src, name)
            if any(member in members[cls] for cls in classes):
                continue
            line = name.start_point[0] + 1
            problems.append(f"{rel}:{line}: у {', '.join(sorted(classes))} нет поля '{member}' (обращение {text(src, obj)}.{member})")

    # 5. класс нашего мода используется, но не импортирован и лежит в другом пакете
    for path, (src, tree) in trees.items():
        rel = os.path.relpath(path, ROOT)
        pkg = package_of(src, tree.root_node)
        single, wild = simple_imports(src, tree.root_node)
        declared_here = set()
        for node in walk(tree.root_node):
            if node.type in ("class_declaration", "interface_declaration", "record_declaration", "enum_declaration"):
                name_node = node.child_by_field_name("name")
                if name_node is not None:
                    declared_here.add(text(src, name_node))
        used = {}
        for node in walk(tree.root_node):
            target = None
            if node.type == "type_identifier":
                # NetData.Track — это вложенный класс, а не отдельный
                if node.parent is not None and node.parent.type in ("scoped_type_identifier", "scoped_identifier"):
                    if node.prev_sibling is not None and node.prev_sibling.type == ".":
                        continue
                target = node
            elif node.type in ("method_invocation", "field_access"):
                obj = node.child_by_field_name("object")
                if obj is not None and obj.type == "identifier":
                    target = obj
            elif node.type == "object_creation_expression":
                t = node.child_by_field_name("type")
                if t is not None and t.type == "type_identifier":
                    target = t
            if target is None:
                continue
            name = text(src, target)
            if name in known and name not in declared_here:
                used.setdefault(name, target)
        for name, node in used.items():
            if name in single or pkg in packages.get(name, ()):
                continue
            if packages.get(name, set()) & wild:
                continue
            line = node.start_point[0] + 1
            problems.append(f"{rel}:{line}: класс {name} не импортирован (пакеты {sorted(packages.get(name, ()))})")

    # 6. Font.draw(PoseStack, ...) — перегрузка из 1.19, в 1.20.1 её нет
    for path, (src, tree) in trees.items():
        rel = os.path.relpath(path, ROOT)
        for node in walk(tree.root_node):
            if node.type != "method_invocation":
                continue
            obj = node.child_by_field_name("object")
            name = node.child_by_field_name("name")
            if obj is None or name is None or text(src, name) not in ("draw", "drawShadow", "drawInternal"):
                continue
            if obj.type != "identifier" or not text(src, obj).endswith("font"):
                continue
            first = node.child_by_field_name("arguments")
            if first is None:
                continue
            args = [c for c in first.children if c.type not in ("(", ")", ",")]
            if args and args[0].type == "method_invocation" and "pose" in text(src, args[0]):
                line = name.start_point[0] + 1
                problems.append(f"{rel}:{line}: font.draw с PoseStack не существует в 1.20.1, рисуйте через g.drawString")

    for line in problems:
        print(line)
    print(f"\nфайлов: {len(files)}, классов: {len(known)}, проблем: {len(problems)}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
