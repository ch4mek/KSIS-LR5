/**
 * HTML-страница веб-интерфейса для просмотра файлового хранилища.
 * Отдаётся браузерам при запросе корневого каталога или подкаталогов.
 * Использует JavaScript fetch() для взаимодействия с REST API.
 */
public class WebUI {

    public static final String HTML = """
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>\u0424\u0430\u0439\u043B\u043E\u0432\u043E\u0435 \u0445\u0440\u0430\u043D\u0438\u043B\u0438\u0449\u0435</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #f5f5f5;
            color: #333;
            min-height: 100vh;
        }
        .header {
            background: #fff;
            border-bottom: 1px solid #ddd;
            padding: 16px 24px;
        }
        .header h1 {
            font-size: 18px;
            font-weight: 600;
            color: #333;
        }
        .header p {
            font-size: 13px;
            color: #888;
            margin-top: 2px;
        }
        .container {
            max-width: 960px;
            margin: 24px auto;
            padding: 0 16px;
        }

        /* Breadcrumb */
        .breadcrumb {
            background: #fff;
            padding: 10px 16px;
            border: 1px solid #ddd;
            border-radius: 4px;
            margin-bottom: 16px;
            display: flex;
            align-items: center;
            gap: 4px;
            font-size: 13px;
            flex-wrap: wrap;
        }
        .breadcrumb a {
            color: #555;
            text-decoration: none;
            cursor: pointer;
        }
        .breadcrumb a:hover { color: #000; text-decoration: underline; }
        .breadcrumb .separator { color: #aaa; }
        .breadcrumb .current { color: #333; font-weight: 600; }

        /* Action bar */
        .actions {
            display: flex;
            gap: 8px;
            margin-bottom: 16px;
            flex-wrap: wrap;
            align-items: center;
        }
        .btn {
            padding: 7px 16px;
            border: 1px solid #ccc;
            border-radius: 4px;
            cursor: pointer;
            font-size: 13px;
            font-weight: 400;
            background: #fff;
            color: #333;
            transition: background 0.15s, border-color 0.15s;
            display: inline-flex;
            align-items: center;
            gap: 5px;
        }
        .btn:hover { background: #f0f0f0; border-color: #999; }
        .btn-primary { background: #555; color: #fff; border-color: #555; }
        .btn-primary:hover { background: #333; }
        .btn-danger { color: #c33; border-color: #c33; }
        .btn-danger:hover { background: #c33; color: #fff; }
        .btn-sm { padding: 4px 10px; font-size: 12px; }

        .file-input-wrapper {
            position: relative;
            overflow: hidden;
            display: inline-block;
        }
        .file-input-wrapper input[type="file"] {
            position: absolute;
            left: 0; top: 0;
            opacity: 0;
            width: 100%;
            height: 100%;
            cursor: pointer;
        }

        /* File list */
        .file-list {
            background: #fff;
            border: 1px solid #ddd;
            border-radius: 4px;
            overflow: hidden;
        }
        .file-list-header {
            display: flex;
            padding: 8px 16px;
            border-bottom: 1px solid #ddd;
            background: #fafafa;
            font-size: 12px;
            font-weight: 600;
            color: #666;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        .file-list-header .col-name { flex: 1; }
        .file-list-header .col-size { width: 100px; text-align: right; }
        .file-list-header .col-date { width: 180px; text-align: right; }
        .file-list-header .col-actions { width: 160px; text-align: right; }

        .file-item {
            display: flex;
            align-items: center;
            padding: 8px 16px;
            border-bottom: 1px solid #eee;
            transition: background 0.1s;
            gap: 8px;
        }
        .file-item:last-child { border-bottom: none; }
        .file-item:hover { background: #fafafa; }
        .file-icon { font-size: 16px; width: 24px; text-align: center; color: #888; }
        .file-name {
            flex: 1;
            font-size: 13px;
            cursor: pointer;
            color: #333;
            text-decoration: none;
        }
        .file-name:hover { color: #000; text-decoration: underline; }
        .file-size { font-size: 12px; color: #888; width: 100px; text-align: right; }
        .file-date { font-size: 12px; color: #888; width: 180px; text-align: right; }
        .file-actions { display: flex; gap: 4px; width: 160px; justify-content: flex-end; }
        .empty-state {
            text-align: center;
            padding: 48px 20px;
            color: #999;
            font-size: 14px;
        }

        /* Modal */
        .modal-overlay {
            display: none;
            position: fixed;
            top: 0; left: 0;
            width: 100%; height: 100%;
            background: rgba(0,0,0,0.4);
            z-index: 1000;
            justify-content: center;
            align-items: center;
        }
        .modal-overlay.active { display: flex; }
        .modal {
            background: #fff;
            border-radius: 6px;
            padding: 24px;
            width: 90%;
            max-width: 440px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.15);
        }
        .modal h2 { font-size: 16px; margin-bottom: 16px; font-weight: 600; }
        .modal input[type="text"] {
            width: 100%;
            padding: 8px 12px;
            border: 1px solid #ccc;
            border-radius: 4px;
            font-size: 13px;
            margin-bottom: 12px;
        }
        .modal input[type="text"]:focus { outline: none; border-color: #888; }
        .modal .modal-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px; }

        /* Status bar */
        .status {
            margin-bottom: 12px;
            padding: 8px 12px;
            border-radius: 4px;
            font-size: 13px;
            display: none;
            border: 1px solid transparent;
        }
        .status.success { display: block; background: #e8f5e9; color: #2e7d32; border-color: #c8e6c9; }
        .status.error { display: block; background: #fbe9e7; color: #c62828; border-color: #ffcdd2; }
        .status.info { display: block; background: #e3f2fd; color: #1565c0; border-color: #bbdefb; }

        /* File info panel */
        .info-panel {
            background: #fff;
            border: 1px solid #ddd;
            border-radius: 4px;
            padding: 16px;
            margin-bottom: 16px;
            display: none;
        }
        .info-panel.active { display: block; }
        .info-panel h3 { margin-bottom: 8px; font-size: 14px; font-weight: 600; color: #555; }
        .info-row { display: flex; padding: 4px 0; font-size: 13px; }
        .info-label { width: 140px; color: #888; }
        .info-value { flex: 1; color: #333; }
    </style>
</head>
<body>
    <div class="header">
        <h1>\u0424\u0430\u0439\u043B\u043E\u0432\u043E\u0435 \u0445\u0440\u0430\u043D\u0438\u043B\u0438\u0449\u0435</h1>
        <p>REST API \u2014 \u0443\u0434\u0430\u043B\u0451\u043D\u043D\u043E\u0435 \u0445\u0440\u0430\u043D\u0438\u043B\u0438\u0449\u0435 \u0444\u0430\u0439\u043B\u043E\u0432</p>
    </div>

    <div class="container">
        <div class="breadcrumb" id="breadcrumb">

            <a onclick="navigateTo(\\'/\\')">\u041A\u043E\u0440\u043D\u0435\u0432\u043E\u0439 \u043A\u0430\u0442\u0430\u043B\u043E\u0433</a>
        </div>

        <div class="actions">
            <div class="file-input-wrapper">
                <button class="btn btn-primary">\u0417\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0444\u0430\u0439\u043B</button>
                <input type="file" id="fileInput" onchange="uploadFile(this.files[0])">
            </div>
            <button class="btn" onclick="showCreateFolderModal()">\u041D\u043E\u0432\u0430\u044F \u043F\u0430\u043F\u043A\u0430</button>
            <button class="btn" onclick="showCopyModal()">\u041A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u0444\u0430\u0439\u043B</button>
            <button class="btn" onclick="refreshListing()">\u041E\u0431\u043D\u043E\u0432\u0438\u0442\u044C</button>
        </div>

        <div class="status" id="status"></div>

        <div class="info-panel" id="infoPanel">
            <h3>\u0418\u043D\u0444\u043E\u0440\u043C\u0430\u0446\u0438\u044F \u043E \u0444\u0430\u0439\u043B\u0435</h3>
            <div class="info-row"><span class="info-label">\u0418\u043C\u044F:</span><span class="info-value" id="infoName">-</span></div>
            <div class="info-row"><span class="info-label">\u0420\u0430\u0437\u043C\u0435\u0440:</span><span class="info-value" id="infoSize">-</span></div>
            <div class="info-row"><span class="info-label">\u0418\u0437\u043C\u0435\u043D\u0451\u043D:</span><span class="info-value" id="infoModified">-</span></div>
            <div class="info-row"><span class="info-label">\u0422\u0438\u043F:</span><span class="info-value" id="infoType">-</span></div>
        </div>

        <div class="file-list" id="fileList">
            <div class="file-list-header">
                <span class="col-name">\u0418\u043C\u044F</span>
                <span class="col-size">\u0420\u0430\u0437\u043C\u0435\u0440</span>
                <span class="col-date">\u0418\u0437\u043C\u0435\u043D\u0451\u043D</span>
                <span class="col-actions">\u0414\u0435\u0439\u0441\u0442\u0432\u0438\u044F</span>
            </div>
            <div id="fileItems">
                <div class="empty-state">
                    <p>\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...</p>
                </div>
            </div>
        </div>
    </div>

    <!-- \u0421\u043E\u0437\u0434\u0430\u043D\u0438\u0435 \u043F\u0430\u043F\u043A\u0438 -->
    <div class="modal-overlay" id="folderModal">
        <div class="modal">
            <h2>\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u043D\u043E\u0432\u0443\u044E \u043F\u0430\u043F\u043A\u0443</h2>
            <input type="text" id="folderName" placeholder="\u0418\u043C\u044F \u043F\u0430\u043F\u043A\u0438...">
            <div class="modal-actions">
                <button class="btn" onclick="closeModal('folderModal')">\u041E\u0442\u043C\u0435\u043D\u0430</button>
                <button class="btn btn-primary" onclick="createFolder()">\u0421\u043E\u0437\u0434\u0430\u0442\u044C</button>
            </div>
        </div>
    </div>

    <!-- \u041A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u043D\u0438\u0435 \u0444\u0430\u0439\u043B\u0430 -->
    <div class="modal-overlay" id="copyModal">
        <div class="modal">
            <h2>\u041A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u0444\u0430\u0439\u043B</h2>
            <p style="font-size:13px;color:#888;margin-bottom:12px;">\u0423\u043A\u0430\u0436\u0438\u0442\u0435 \u0438\u0441\u0445\u043E\u0434\u043D\u044B\u0439 \u0438 \u043A\u043E\u043D\u0435\u0447\u043D\u044B\u0439 \u043F\u0443\u0442\u0438.</p>
            <input type="text" id="copySource" placeholder="\u0418\u0441\u0445\u043E\u0434\u043D\u044B\u0439 \u043F\u0443\u0442\u044C (\u043D\u0430\u043F\u0440. /docs/file.txt)">
            <input type="text" id="copyDest" placeholder="\u041A\u043E\u043D\u0435\u0447\u043D\u044B\u0439 \u043F\u0443\u0442\u044C (\u043D\u0430\u043F\u0440. /backup/file.txt)">
            <div class="modal-actions">
                <button class="btn" onclick="closeModal('copyModal')">\u041E\u0442\u043C\u0435\u043D\u0430</button>
                <button class="btn btn-primary" onclick="copyFile()">\u041A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u0442\u044C</button>
            </div>
        </div>
    </div>

    <script>
        var currentPath = '/';

        document.addEventListener('DOMContentLoaded', function() {
            navigateTo('/');
        });

        function showStatus(message, type) {
            var el = document.getElementById('status');
            el.textContent = message;
            el.className = 'status ' + type;
            setTimeout(function() { el.className = 'status'; }, 5000);
        }

        function navigateTo(path) {
            if (!path.endsWith('/')) path += '/';
            currentPath = path;
            document.getElementById('infoPanel').classList.remove('active');
            updateBreadcrumb(path);
            loadDirectory(path);
        }

        function updateBreadcrumb(path) {
            var bc = document.getElementById('breadcrumb');
            var html = '<a onclick="navigateTo(\\'/\\')">\u041A\u043E\u0440\u043D\u0435\u0432\u043E\u0439 \u043A\u0430\u0442\u0430\u043B\u043E\u0433</a>';

            if (path !== '/') {
                var parts = path.split('/').filter(function(p) { return p; });
                var accumulated = '';
                for (var i = 0; i < parts.length; i++) {
                    accumulated += '/' + parts[i];
                    var navPath = accumulated + '/';
                    if (i < parts.length - 1) {
                        html += ' <span class="separator">/</span> ';
                        html += '<a onclick="navigateTo(\\'' + navPath + '\\')">' + parts[i] + '</a>';
                    } else {
                        html += ' <span class="separator">/</span> ';
                        html += '<span class="current">' + parts[i] + '</span>';
                    }
                }
            }
            bc.innerHTML = html;
        }

        function loadDirectory(path) {
            var xhr = new XMLHttpRequest();
            xhr.open('GET', path, true);
            xhr.setRequestHeader('Accept', 'application/json');
            xhr.onreadystatechange = function() {
                if (xhr.readyState !== 4) return;
                if (xhr.status === 200) {
                    try {
                        var data = JSON.parse(xhr.responseText);
                        renderFileList(data);
                    } catch(e) {
                        showStatus('\u041E\u0448\u0438\u0431\u043A\u0430 \u043E\u0431\u0440\u0430\u0431\u043E\u0442\u043A\u0438 \u043E\u0442\u0432\u0435\u0442\u0430', 'error');
                    }
                } else if (xhr.status === 404) {
                    document.getElementById('fileItems').innerHTML =
                        '<div class="empty-state"><p>\u041A\u0430\u0442\u0430\u043B\u043E\u0433 \u043F\u0443\u0441\u0442 \u0438\u043B\u0438 \u043D\u0435 \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u0435\u0442.</p></div>';
                } else {
                    showStatus('\u041E\u0448\u0438\u0431\u043A\u0430 \u0437\u0430\u0433\u0440\u0443\u0437\u043A\u0438: HTTP ' + xhr.status, 'error');
                }
            };
            xhr.send();
        }

        function renderFileList(data) {
            var container = document.getElementById('fileItems');

            if (!data.files || data.files.length === 0) {
                container.innerHTML = '<div class="empty-state"><p>\u041A\u0430\u0442\u0430\u043B\u043E\u0433 \u043F\u0443\u0441\u0442. \u0417\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u0435 \u0444\u0430\u0439\u043B \u0438\u043B\u0438 \u0441\u043E\u0437\u0434\u0430\u0439\u0442\u0435 \u043F\u0430\u043F\u043A\u0443.</p></div>';
                return;
            }

            var html = '';
            for (var i = 0; i < data.files.length; i++) {
                var entry = data.files[i];
                var fullPath = (data.path === '/' ? '/' : data.path) + entry.name;

                if (entry.type === 'directory') {
                    html += '<div class="file-item">';
                    html += '<span class="file-icon">\uD83D\uDCC1</span>';
                    html += '<a class="file-name" onclick="navigateTo(\\'' + fullPath + '/\\')">' + entry.name + '/</a>';
                    html += '<span class="file-size">\u2014</span>';
                    html += '<span class="file-date">\u2014</span>';
                    html += '<div class="file-actions">';
                    html += '<button class="btn btn-danger btn-sm" onclick="deleteItem(\\'' + fullPath + '/\\')">\u0423\u0434\u0430\u043B\u0438\u0442\u044C</button>';
                    html += '</div>';
                    html += '</div>';
                } else {
                    var sizeStr = formatSize(entry.size);
                    var dateStr = entry.lastModified ? new Date(entry.lastModified).toLocaleString('ru-RU') : '\u2014';
                    html += '<div class="file-item">';
                    html += '<span class="file-icon">\uD83D\uDCC4</span>';
                    html += '<a class="file-name" href="' + fullPath + '" download>' + entry.name + '</a>';
                    html += '<span class="file-size">' + sizeStr + '</span>';
                    html += '<span class="file-date">' + dateStr + '</span>';
                    html += '<div class="file-actions">';
                    html += '<button class="btn btn-sm" onclick="showFileInfo(\\'' + fullPath + '\\', \\'' + entry.name + '\\')">\u0418\u043D\u0444\u043E</button>';
                    html += '<button class="btn btn-danger btn-sm" onclick="deleteItem(\\'' + fullPath + '\\')">\u0423\u0434\u0430\u043B\u0438\u0442\u044C</button>';
                    html += '</div>';
                    html += '</div>';
                }
            }
            container.innerHTML = html;
        }

        function formatSize(bytes) {
            if (bytes < 0) return '?';
            if (bytes === 0) return '0 \u0411';
            var units = ['\u0411', '\u041A\u0411', '\u041C\u0411', '\u0413\u0411'];
            var i = Math.floor(Math.log(bytes) / Math.log(1024));
            return (bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + ' ' + units[i];
        }

        function uploadFile(file) {
            if (!file) return;
            var path = currentPath + file.name;
            var xhr = new XMLHttpRequest();
            xhr.open('PUT', path, true);
            xhr.onreadystatechange = function() {
                if (xhr.readyState !== 4) return;
                if (xhr.status === 201) {
                    showStatus('\u0424\u0430\u0439\u043B \u00AB' + file.name + '\u00BB \u0443\u0441\u043F\u0435\u0448\u043D\u043E \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043D!', 'success');
                } else if (xhr.status === 204) {
                    showStatus('\u0424\u0430\u0439\u043B \u00AB' + file.name + '\u00BB \u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0438\u0441\u0430\u043D.', 'info');
                } else {
                    showStatus('\u041E\u0448\u0438\u0431\u043A\u0430 \u0437\u0430\u0433\u0440\u0443\u0437\u043A\u0438: HTTP ' + xhr.status, 'error');
                }
                document.getElementById('fileInput').value = '';
                loadDirectory(currentPath);
            };
            xhr.send(file);
        }

        function createFolder() {
            var name = document.getElementById('folderName').value.trim();
            if (!name) {
                showStatus('\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0438\u043C\u044F \u043F\u0430\u043F\u043A\u0438', 'error');
                return;
            }
            var path = currentPath + name + '/.keep';
            var xhr = new XMLHttpRequest();
            xhr.open('PUT', path, true);
            xhr.onreadystatechange = function() {
                if (xhr.readyState !== 4) return;
                if (xhr.status === 201 || xhr.status === 204) {
                    showStatus('\u041F\u0430\u043F\u043A\u0430 \u00AB' + name + '\u00BB \u0441\u043E\u0437\u0434\u0430\u043D\u0430!', 'success');
                    closeModal('folderModal');
                    document.getElementById('folderName').value = '';
                    loadDirectory(currentPath);
                } else {
                    showStatus('\u041E\u0448\u0438\u0431\u043A\u0430 \u0441\u043E\u0437\u0434\u0430\u043D\u0438\u044F \u043F\u0430\u043F\u043A\u0438: HTTP ' + xhr.status, 'error');
                }
            };
            xhr.send('');
        }

        function showFileInfo(path, name) {
            var xhr = new XMLHttpRequest();
            xhr.open('HEAD', path, true);
            xhr.onreadystatechange = function() {
                if (xhr.readyState !== 4) return;
                if (xhr.status === 200) {
                    var size = xhr.getResponseHeader('Content-Length');
                    var modified = xhr.getResponseHeader('Last-Modified');
                    var type = xhr.getResponseHeader('Content-Type');
                    document.getElementById('infoName').textContent = name;
                    document.getElementById('infoSize').textContent = size ? formatSize(parseInt(size)) : '\u2014';
                    document.getElementById('infoModified').textContent = modified || '\u2014';
                    document.getElementById('infoType').textContent = type || '\u2014';
                    document.getElementById('infoPanel').classList.add('active');
                } else {
                    showStatus('\u041E\u0448\u0438\u0431\u043A\u0430 \u043F\u043E\u043B\u0443\u0447\u0435\u043D\u0438\u044F \u0438\u043D\u0444\u043E\u0440\u043C\u0430\u0446\u0438\u0438: HTTP ' + xhr.status, 'error');
                }
            };
            xhr.send();
        }

        function deleteItem(path) {
            if (!confirm('\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u00AB' + path + '\u00BB?')) return;
            var xhr = new XMLHttpRequest();
            xhr.open('DELETE', path, true);
            xhr.onreadystatechange = function() {
                if (xhr.readyState !== 4) return;
                if (xhr.status === 204) {
                    showStatus('\u0423\u0441\u043F\u0435\u0448\u043D\u043E \u0443\u0434\u0430\u043B\u0435\u043D\u043E!', 'success');
                    loadDirectory(currentPath);
                } else if (xhr.status === 404) {
                    showStatus('\u0424\u0430\u0439\u043B \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D.', 'error');
                    loadDirectory(currentPath);
                } else {
                    showStatus('\u041E\u0448\u0438\u0431\u043A\u0430 \u0443\u0434\u0430\u043B\u0435\u043D\u0438\u044F: HTTP ' + xhr.status, 'error');
                }
            };
            xhr.send();
        }

        function copyFile() {
            var source = document.getElementById('copySource').value.trim();
            var dest = document.getElementById('copyDest').value.trim();
            if (!source || !dest) {
                showStatus('\u0423\u043A\u0430\u0436\u0438\u0442\u0435 \u043E\u0431\u0430 \u043F\u0443\u0442\u0438.', 'error');
                return;
            }
            var xhr = new XMLHttpRequest();
            xhr.open('PUT', dest, true);
            xhr.setRequestHeader('X-Copy-From', source);
            xhr.onreadystatechange = function() {
                if (xhr.readyState !== 4) return;
                if (xhr.status === 201) {
                    showStatus('\u0424\u0430\u0439\u043B \u0443\u0441\u043F\u0435\u0448\u043D\u043E \u0441\u043A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u043D!', 'success');
                    closeModal('copyModal');
                    document.getElementById('copySource').value = '';
                    document.getElementById('copyDest').value = '';
                    loadDirectory(currentPath);
                } else if (xhr.status === 204) {
                    showStatus('\u0424\u0430\u0439\u043B \u0441\u043A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u043D (\u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0438\u0441\u0430\u043D).', 'info');
                    closeModal('copyModal');
                    loadDirectory(currentPath);
                } else {
                    showStatus('\u041E\u0448\u0438\u0431\u043A\u0430 \u043A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u043D\u0438\u044F: HTTP ' + xhr.status, 'error');
                }
            };
            xhr.send();
        }

        function refreshListing() {
            loadDirectory(currentPath);
        }

        function showCreateFolderModal() {
            document.getElementById('folderModal').classList.add('active');
            document.getElementById('folderName').focus();
        }

        function showCopyModal() {
            document.getElementById('copyModal').classList.add('active');
            document.getElementById('copySource').focus();
        }

        function closeModal(id) {
            document.getElementById(id).classList.remove('active');
        }

        document.querySelectorAll('.modal-overlay').forEach(function(overlay) {
            overlay.addEventListener('click', function(e) {
                if (e.target === overlay) overlay.classList.remove('active');
            });
        });
    </script>
</body>
</html>
""";
}
