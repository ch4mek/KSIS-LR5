 # KSIS-5LR: Remote File Storage REST API

Удаленное файловое хранилище с REST API по протоколу HTTP.

## Требования

- Java 17+ (JDK)
- curl (для тестирования)

## Сборка и запуск

### Компиляция

```bash
javac -d out src/Main.java src/StorageService.java src/FileStorageHandler.java src/WebUI.java
```

### Запуск сервера

```bash
java -cp out Main [порт] [путь_к_хранилищу]
```

Параметры:
- **порт** — HTTP-порт (по умолчанию: 8080)
- **путь_к_хранилищу** — локальная директория для хранения файлов (по умолчанию: `./storage`)

Пример:

```bash
java -cp out Main 8080 ./storage
```

После запуска сервер доступен по адресу: `http://localhost:8080/`

## REST API

URL-путь запроса определяет логическое расположение файла в хранилище:

```
http://localhost:8080/path/to/file.txt → файл file.txt в каталоге /path/to
```

### Поддерживаемые методы

| Метод | Описание | Код успеха |
|-------|----------|------------|
| `GET` | Загрузка файла или получение списка каталога | 200 OK |
| `PUT` | Загрузка файла (с перезаписью) | 201 Created / 204 No Content |
| `PUT` + `X-Copy-From` | Копирование файла | 201 Created / 204 No Content |
| `HEAD` | Получение информации о файле (без содержимого) | 200 OK |
| `DELETE` | Удаление файла или каталога | 204 No Content |

### Коды состояния HTTP

| Код | Значение | Когда используется |
|-----|----------|--------------------|
| 200 | OK | Успешный GET, HEAD |
| 201 | Created | Новый файл создан через PUT |
| 204 | No Content | Файл перезаписан (PUT), успешное удаление (DELETE) |
| 400 | Bad Request | Некорректный путь |
| 404 | Not Found | Файл/каталог не найден |
| 405 | Method Not Allowed | Неподдерживаемый HTTP-метод |
| 500 | Internal Server Error | Внутренняя ошибка сервера |

### Заголовки ответов

| Заголовок | Методы | Описание |
|-----------|--------|----------|
| `Content-Type` | GET | `application/octet-stream` для файлов, `application/json` для каталогов |
| `Content-Length` | GET, HEAD | Размер файла в байтах |
| `Last-Modified` | GET, HEAD | Дата последнего изменения (RFC 1123) |
| `Content-Disposition` | GET | `attachment; filename="..."` для скачивания файлов |

## Примеры использования (curl)

### 1. Загрузка файла в хранилище (PUT)

Загрузка нового файла — возвращает **201 Created**:

```bash
curl -X PUT --data-binary "Hello, World!" http://localhost:8080/docs/hello.txt
```

Перезапись существующего файла — возвращает **204 No Content**:

```bash
curl -X PUT --data-binary "Updated content" http://localhost:8080/docs/hello.txt
```

Загрузка файла с диска:

```bash
curl -X PUT --data-binary @myfile.pdf http://localhost:8080/documents/myfile.pdf
```

### 2. Получение файла из хранилища (GET)

```bash
curl http://localhost:8080/docs/hello.txt
```

Ответ:
```
Hello, World!
```

Также работает в браузере: `http://localhost:8080/docs/hello.txt`

### 3. Получение списка файлов каталога (GET)

Запрос пути, заканчивающегося на `/`, возвращает JSON-список содержимого каталога:

```bash
curl http://localhost:8080/docs/
```

Ответ:
```json
{
  "path": "/docs/",
  "files": [
    {
      "name": "hello.txt",
      "type": "file",
      "size": 13,
      "lastModified": "2026-05-04T16:08:51.739Z"
    }
  ]
}
```

Корневой каталог:

```bash
curl http://localhost:8080/
```

### 4. Получение информации о файле (HEAD)

Возвращает HTTP-заголовки с метаданными файла без содержимого:

```bash
curl -I http://localhost:8080/docs/hello.txt
```

Ответ:
```
HTTP/1.1 200 OK
Content-disposition: attachment; filename="hello.txt"
Content-type: application/octet-stream
Content-length: 13
Last-modified: Sun, 04 May 2026 16:08:51 GMT
```

### 5. Удаление файла (DELETE)

```bash
curl -X DELETE http://localhost:8080/docs/hello.txt
```

Возвращает **204 No Content**.

### 6. Удаление каталога (DELETE)

Удаляет каталог рекурсивно со всем содержимым:

```bash
curl -X DELETE http://localhost:8080/docs/
```

### 7. Копирование файла (дополнительное задание)

Используется метод PUT с заголовком `X-Copy-From`, указывающим на исходный файл:

```bash
curl -X PUT -H "X-Copy-From: /docs/hello.txt" http://localhost:8080/archive/hello_backup.txt
```

- Если целевой файл не существовал → **201 Created**
- Если целевой файл существовал → **204 No Content** (перезаписан)

## Архитектура

Проект состоит из четырёх классов:

- **`Main.java`** — точка входа, запуск `HttpServer` на заданном порту
- **`FileStorageHandler.java`** — обработчик HTTP-запросов, роутинг по методам (GET/PUT/HEAD/DELETE)
- **`StorageService.java`** — слой работы с файловой системой (чтение, запись, удаление, копирование, листинг)
- **`WebUI.java`** — HTML-страница веб-интерфейса для просмотра хранилища в браузере

Используется встроенный `com.sun.net.httpserver.HttpServer` из JDK — без внешних зависимостей.

## Безопасность

- **Защита от path traversal**: пути с `..` отклоняются с кодом 400
- **Изоляция хранилища**: все операции ограничены корневым каталогом хранилища
- **Нормализация путей**: пути нормализуются и проверяются на принадлежность к корню хранилища
