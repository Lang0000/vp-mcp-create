# vp-mcp-create — створення діаграм у Visual Paradigm через MCP

[English](README.md) · **Українська**

Плагін для [Visual Paradigm](https://www.visual-paradigm.com/) (перевірено на Community Edition 18.1, macOS), який
запускає всередині Visual Paradigm сервер [Model Context Protocol](https://modelcontextprotocol.io/). Після цього
MCP-клієнт, наприклад Claude Code, може **створювати, змінювати, розкладати, експортувати й зберігати діаграми** у
відкритому у Visual Paradigm проєкті: класів, варіантів використання, діяльності (з доріжками), розгортання,
компонентів, послідовності, блок-схеми, станів тощо. Ви описуєте вузли та зв'язки у JSON, а плагін створює справжні
елементи UML-моделі, підганяє розміри фігур під текст, розкладає діаграму й прокладає лінії.

```
MCP-клієнт (Claude Code, ...)  ──HTTP, JSON-RPC 2.0──▶  127.0.0.1:8900/mcp
                                                           │  плагін vpmcp.create у Visual Paradigm
                                                           ▼
                                          Visual Paradigm Open API  →  відкритий проєкт .vpp
```

## Як це працює

- **Плагін Visual Paradigm.** `plugin.xml` реєструє плагін; під час старту (`VpCreatePlugin`) він відкриває
  невеликий HTTP-сервер на `127.0.0.1:8900`. У JRE, що постачається з Visual Paradigm, немає `jdk.httpserver`, тому
  сервер — це мінімальна реалізація транспорту MCP *Streamable HTTP* на сокетах (`core/McpHttpServer`,
  `core/McpProtocol`).
- **Потік інтерфейсу.** Кожен виклик інструмента виконується в потоці подій Swing (`vp/EdtInvoker`), бо Open API не
  потокобезпечний. Якщо у Visual Paradigm відкрито модальне вікно, виклик через 180 с завершується помилкою і пізніше
  вже не виконується.
- **Спершу перевірка, потім побудова.** Запит повністю перевіряється до створення будь-чого; якщо створення
  зірветься посередині, усе, що встиг створити цей виклик, відкочується, а клієнт отримує зрозумілу помилку
  (`isError: true`). Visual Paradigm не падає.
- **Елементи моделі, а не картинки.** Вузли стають справжніми елементами моделі (класи з атрибутами й операціями,
  актори, варіанти використання, дії, доріжки, вузли розгортання, лінії життя…), зв'язки — справжніми відношеннями
  (узагальнення, композиція з кратностями, include/extend, потоки керування з умовами, повідомлення…). Результат —
  звичайна діаграма Visual Paradigm, яку можна далі редагувати вручну.
- **Розміри, розкладка, лінії.** Розміри фігур рахуються з тексту (`vp/Sizer`). Розкладка за замовчуванням
  `layered` (`layout/LayeredLayout`, у стилі Сугіями, без залежності від VP) уникає перекриттів, підганяє контейнери
  під вміст і робить із розділів діяльності колонки доріжки. Доступні й вбудовані розкладки VP (`hierarchic`,
  `orthogonal`, `organic`, `tree`, …). Visual Paradigm не перебудовує лінії й не переставляє підписи для елементів,
  створених через API, тож це робить плагін: ортогональні маршрути між серединами сторін на діаграмах із
  прямокутними лініями (діяльності, блок-схеми), перебудова засобами VP — на решті.
- **Меню.** **Tools → MCP Create Server** показує, чи працює сервер, його адресу й файл журналу (`vpcreate.log` у
  каталозі плагіна).

## Що потрібно

- Visual Paradigm (достатньо Community Edition) — збірка використовує його `openapi.jar`, якого **немає** в цьому
  репозиторії і який ніколи не вкладається в плагін.
- JDK 11 або новіший і Maven 3.
- `build.sh` розрахований на macOS. На Windows і Linux збирайте через Maven і копіюйте файли вручну (див. нижче).

## Встановлення

### macOS

```bash
git clone https://github.com/Lang0000/vp-mcp-create.git
cd vp-mcp-create
./build.sh            # збирає target/vpmcp.create.jar і встановлює плагін
./build.sh --check    # те саме + офлайн-перевірки (розкладка, парсер атрибутів, HTTP / JSON-RPC)
```

Значення за замовчуванням, які можна змінити змінними середовища:

| Змінна | За замовчуванням |
|---|---|
| `VP_HOME` | `/Applications/Visual Paradigm CE.app` |
| `VP_PLUGINS` | `~/Library/Application Support/VisualParadigm/plugins` |
| `JAVA_HOME` | Homebrew `openjdk@17`, якщо є, інакше `/usr/libexec/java_home` |

Після першого встановлення перезапустіть Visual Paradigm. **Tools → MCP Create Server** має показувати `RUNNING`.

### Windows / Linux

1. Знайдіть `openapi.jar` у каталозі встановлення Visual Paradigm (`<каталог VP>/lib/openapi.jar`).
2. Зберіть: `mvn -Dvp.openapi="<шлях до openapi.jar>" clean package`.
3. Створіть каталог `vpmcp.create` у каталозі плагінів Visual Paradigm і покладіть туди `plugin.xml`,
   `icons/mcp-create.png` і `lib/vpmcp.create.jar` (з `target/`).
4. Перезапустіть Visual Paradigm і перевірте **Tools → MCP Create Server**.

### Підключення MCP-клієнта

Claude Code:

```bash
claude mcp add --transport http vp-create http://127.0.0.1:8900/mcp
```

Будь-який інший клієнт із підтримкою транспорту Streamable HTTP підключається за тією самою адресою.

### Налаштування (необов'язково)

Файл `vpcreate.properties` у каталозі плагіна:

```properties
port=8900
# dev=true додає інструмент reload_plugin: перезапуск сервера з нового jar без перезапуску Visual Paradigm
```

Сервер слухає лише `127.0.0.1` і не має автентифікації; запити з нелокальним заголовком `Origin` відхиляються.
Будь-яка програма на вашому комп'ютері, що може звернутися до цього порту, може змінювати відкритий проєкт.

## Інструменти

| Інструмент | Що робить |
|---|---|
| `create_diagram` | Нова діаграма з вузлів і зв'язків: розміри за текстом, розкладка, відкриття у Visual Paradigm. Повертає URL діаграми та для кожного id із запиту — `model_id` / `shape_id` / `connector_id`. |
| `add_elements` | Додає вузли й зв'язки в наявну діаграму. Зв'язки можуть посилатися на нові вузли (id із запиту) або на наявні (id фігури чи моделі). `layout`: `append` (лише нові вузли поряд із наявним), `auto` (уся діаграма), `none`. |
| `update_element` | Назва, документація, стереотипи, атрибути / операції (замінити, додати, видалити), abstract, підпис / умова / кратності / ролі зв'язку, форматування, межі. |
| `delete_element` | Видаляє вигляд (за замовчуванням) або й елемент моделі (`from_model: true`); `delete_diagram: true` видаляє діаграму. |
| `layout_diagram` | `layered` (власна розкладка з контейнерами й доріжками) або вбудовані у VP `hierarchic`, `orthogonal`, `organic`, `tree`, `circular`, …; `route_orthogonal` перебудовує лінії, не рухаючи фігур. |
| `export_diagram` | PNG / SVG / PDF / JPG у вказаний каталог; `scale: 2` — якість для друку. |
| `list_diagrams` | id, назва, тип, URL і кількість фігур; `include_vocabulary: true` — підтримувані типи діаграм і види вузлів та зв'язків. |
| `save_project` | Зберігає в поточний `.vpp` або за `path` (`overwrite: true` — замінити файл). |

### Запит `create_diagram`

```json
{
  "type": "class",
  "name": "Рис. 2.3 Діаграма класів",
  "nodes": [
    {"id": "n1", "kind": "class", "name": "ThermalImage",
     "attributes": ["+path: str", "+width: int"],
     "operations": ["+load(): Tensor"]},
    {"id": "n2", "kind": "class", "name": "Annotation",
     "style": {"fill": "#FFF2CC", "line_color": "#BF9000", "bold": true}}
  ],
  "edges": [
    {"from": "n1", "to": "n2", "kind": "composition",
     "label": "має", "multiplicity": {"from": "1", "to": "0..*"}}
  ],
  "layout": "auto"
}
```

Більше прикладів у [examples/](examples/): `class`, `usecase`, `activity`, `deployment`, `sequence`, `component`,
`flowchart`.

- **Типи діаграм:** `class`, `usecase`, `activity`, `deployment`, `sequence`, `component`, `flowchart`, `state`,
  `package`, `object`, `communication`, `er`, … (`bpmn` і `dfd` є у словнику, але в Community Edition недоступні —
  інструмент про це повідомляє).
- **Види вузлів:** class, interface, abstract_class, enum, package, note · actor, usecase, system · initial, final,
  flow_final, action, decision, merge, fork, join, object, partition · node, device, execution_environment,
  artifact, component · lifeline · terminator, process, data, document, database, predefined_process,
  preparation · state. Будь-який інший тип Open API: `"raw:<ModelType>"`.
- **Види зв'язків:** association, aggregation, composition, generalization, realization, dependency, usage,
  include, extend, control_flow, object_flow, deploy, manifest, anchor, message, flowline, transition. Напрям — як у
  стрілки UML: generalization/realization — від нащадка до батька, aggregation/composition — від цілого (ромб) до
  частини, include — від базового варіанта, extend — від розширення до базового.
- **Вкладення:** `"parent": "<id>"` кладе вузол у межу системи, пакет, вузол розгортання або розділ діяльності.
- **Члени класу:** рядки UML (`"-count: int = 0"`, `"+load(path: str): Tensor"`, `"{static} +instance(): App"`) або
  об'єкти `{name, type, visibility, initial, static, abstract, parameters}`.
- **Форматування:** `fill`, `line_color`, `line_weight`, `line_style`, `font_color`, `font_size`, `font_name`,
  `bold`, `italic` (кольори `#RRGGBB`, `fill: "none"` — прозоро) або вкладена форма
  `{"fill": {"color1"}, "line": {...}, "font": {...}}`.
- **Ручне розміщення:** `x`, `y`, `width`, `height` у вузлах разом із `"layout": "none"`.

## Скрипти

Сервер — звичайний HTTP, тож діаграми можна генерувати скриптом, а не з чату: зручно, коли діаграм багато і їх
треба перебудовувати після змін. [examples/call.sh](examples/call.sh) надсилає один виклик інструмента;
[examples/acceptance.py](examples/acceptance.py) — повний клієнт на Python (створити, експортувати, прибрати).
Крок зворотного читання в `acceptance.py` використовує окремий сервер лише для читання на порту 8899
(`get_diagram_by_url`), якого в цьому репозиторії немає; без нього цей крок пропустіть.

## Що варто знати

- **Назви спільні для всього проєкту.** Visual Paradigm не дозволяє двох елементів з однаковою назвою в одному
  просторі імен, тож класифікатор із наявною назвою *повторно використовується* і показується на новій діаграмі
  (`"reused": true` у відповіді). Якщо генерувати в проєкт, де вже є інші діаграми, вони можуть зв'язатися між
  собою, а видалення з `from_model: true` прибирає елемент з усіх діаграм. Набори згенерованих діаграм краще
  будувати в порожньому проєкті.
- **Undo не групується.** В Open API немає способу згрупувати кроки скасування, тож Ctrl+Z скасовує по одному
  елементу. Щоб скасувати цілий виклик `create_diagram`, видаліть діаграму з `delete_diagram: true,
  from_model: true`.
- **Переміщення фігур після розкладки.** `update_element` з `bounds` переміщує фігуру, але не перебудовує її лінії;
  після цього викличте `layout_diagram` з `route_orthogonal` і передайте той самий `direction` (`LR`/`TB`), з яким
  створювали діаграму, інакше маршрути будуть розраховані згори донизу.
- **Обмеження Community Edition:** немає діаграм BPMN і DFD (замість них — діаграми діяльності з розділами та
  діаграма класів або компонентів). На експортованих зображеннях у правому нижньому куті є напис «Powered By Visual
  Paradigm Community Edition»; він може накрити найнижчий вузол, тож залишайте там вільне місце.
- **Діаграми послідовності:** повідомлення самому собі не малюються, а повідомлення-відповіді виглядають як
  звичайні.
- **Діаграми варіантів використання:** варіант, який розширюють (extend), показує секцію «extension points», тож
  дайте йому більшу висоту.
- **Примітки:** видалення лише вигляду примітки відкриває діалог у Visual Paradigm, тому примітки та їхні лінії
  завжди видаляються разом з елементом моделі.
- Після оновлення Visual Paradigm плагін, можливо, доведеться перезібрати (`./build.sh`).

## Структура проєкту

```
plugin.xml, build.sh, pom.xml, icons/
src/main/java/vpmcp/create/
  VpCreatePlugin, ServerHandle         точка входу плагіна, життєвий цикл сервера
  core/   McpHttpServer, McpProtocol   Streamable HTTP на сокетах, JSON-RPC 2.0
  vp/     DiagramBuilder, Kinds, ...   створення елементів, словник типів, стилі, розміри, доріжки, лінії
  layout/ LayeredLayout                шарова розкладка без залежностей від Visual Paradigm
  tools/                               вісім інструментів MCP (+ reload_plugin у режимі dev)
  ui/     StatusActionController       Tools → MCP Create Server
src/test/java/.../OfflineCheck         перевірки без Visual Paradigm (./build.sh --check)
examples/                              JSON-приклади, call.sh, acceptance.py
```

## Ліцензія

[MIT](LICENSE). У jar плагіна вбудовано Gson (Apache License 2.0), див. [THIRD_PARTY.md](THIRD_PARTY.md).
Visual Paradigm та його Open API належать Visual Paradigm International; цей проєкт із ними не пов'язаний.
