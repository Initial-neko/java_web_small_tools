/* Local Windows Java process manager. No third-party front-end dependencies. */
(function () {
  'use strict';
  let root = null;
  let timer = null;
  let generation = 0;
  let loading = false;
  let actionRunning = false;
  let token = '';
  let records = [];
  let supported = true;
  let filter = '';
  let notice = '';
  let noticeError = false;

  const $ = (selector) => root && root.querySelector(selector);

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function button(label, handler, disabled, className) {
    const b = element('button', 'btn btn-sm ' + (className || ''), label);
    b.type = 'button';
    b.disabled = !!disabled;
    if (handler) b.addEventListener('click', handler);
    return b;
  }
  function setNotice(message, isError) {
    notice = message || '';
    noticeError = !!isError;
    if (!root) return;
    const node = $('#jpm-notice');
    if (node) {
      node.textContent = notice;
      node.className = 'jpm-notice' + (noticeError ? ' error' : '');
      node.hidden = !notice;
    }
  }
  function updateView() {
    if (!root) return;
    const summary = $('#jpm-count');
    const tbody = $('#jpm-body');
    if (!tbody) return;
    tbody.replaceChildren();
    const matches = records.filter(p => {
      const name = [p.pid, p.name, p.main, p.executable].join(' ').toLowerCase();
      return name.includes(filter);
    });
    summary.textContent = '显示 ' + matches.length + ' / ' + records.length + ' 个 Java 进程';
    $('#jpm-refresh').disabled = loading || actionRunning;
    if (!matches.length) {
      const tr = document.createElement('tr');
      const td = element('td', 'jpm-empty',
        !supported ? '此平台暂不支持 Windows 进程管理' :
          records.length ? '没有匹配的 Java 进程' : '未发现 java.exe / javaw.exe 进程');
      td.colSpan = 5;
      tr.appendChild(td);
      tbody.appendChild(tr);
      return;
    }
    matches.forEach(p => {
      const tr = document.createElement('tr');
      const pid = element('td', 'jpm-mono', String(p.pid));
      const process = element('td');
      process.appendChild(element('strong', 'jpm-main', p.name || p.executable || 'Java'));
      process.appendChild(element('small', 'jpm-path', p.executable || '可执行路径未知'));
      const started = element('td', '', p.startTime ? new Date(p.startTime).toLocaleString() : '未知');
      const state = element('td');
      state.appendChild(element('span', p.canKill ? 'jpm-state ok' : 'jpm-state locked', p.canKill ? '运行中' : '不可操作'));
      if (p.reason) state.appendChild(element('small', 'jpm-path', p.reason));
      const action = element('td');
      action.appendChild(button('强制结束', () => killProcess(p), !p.canKill || actionRunning, 'jpm-kill'));
      tr.append(pid, process, started, state, action);
      tbody.appendChild(tr);
    });
  }
  async function refresh() {
    if (!root || loading || actionRunning || document.hidden) return;
    loading = true;
    const run = generation;
    updateView();
    try {
      const response = await fetch('/api/system/java-processes', {cache:'no-store',credentials:'same-origin'});
      const data = await response.json();
      if (run !== generation || !root) return;
      if (!response.ok || !data.success) throw new Error(data.message || ('HTTP ' + response.status));
      token = data.token || '';
      records = Array.isArray(data.processes) ? data.processes : [];
      supported = !!data.supported;
      if (!supported) setNotice(data.message || '仅支持 Windows 系统', true);
      else if (noticeError) setNotice('', false);
      const stamp = $('#jpm-updated');
      if (stamp) stamp.textContent = '最近更新：' + new Date().toLocaleTimeString();
    } catch (e) {
      if (run === generation) setNotice('进程刷新失败：' + e.message, true);
    } finally {
      if (run === generation) { loading = false; updateView(); }
    }
  }
  async function killProcess(p) {
    if (!root || actionRunning || !p.canKill || !token) return;
    const message = '确定强制结束以下 Java 进程吗？\n\n'
      + 'PID：' + p.pid + '\n应用：' + (p.name || '未解析')
      + '\n启动时间：' + new Date(p.startTime).toLocaleString()
      + '\n\n强制结束可能导致未保存数据丢失。此操作不可撤销。';
    if (!window.confirm(message)) return;
    const run = generation;
    actionRunning = true;
    setNotice('正在结束 PID ' + p.pid + '…', false);
    updateView();
    try {
      const response = await fetch('/api/system/java-processes/' + encodeURIComponent(p.pid) + '/kill', {
        method:'POST', credentials:'same-origin',
        headers:{'Content-Type':'application/json','X-Process-Token':token},
        body:JSON.stringify({startTime:p.startTime})
      });
      const data = await response.json();
      if (run !== generation || !root) return;
      if (!response.ok || !data.success) throw new Error(data.message || ('HTTP ' + response.status));
      setNotice(data.message || ('PID ' + p.pid + ' 已结束'), false);
    } catch (e) {
      if (run === generation) setNotice('结束失败：' + e.message, true);
    } finally {
      if (run === generation) {
        actionRunning = false;
        updateView();
        refresh();
      }
    }
  }

  function mount(container) {
    unmount();
    root = container;
    root.className = 'jpm-root';
    records = []; token = ''; filter = ''; supported = true;
    loading = false; actionRunning = false; notice = ''; noticeError = false;
    const toolbar = element('div', 'jpm-toolbar');
    const search = element('input', 'jpm-search');
    search.type = 'search';
    search.placeholder = '按 PID、Jar / 主类、Java 路径搜索';
    search.setAttribute('aria-label', '筛选 Java 进程');
    search.addEventListener('input', () => {filter = search.value.trim().toLowerCase(); updateView();});
    toolbar.appendChild(search);
    const refreshButton = button('立即刷新', () => refresh());
    refreshButton.id = 'jpm-refresh';
    toolbar.appendChild(refreshButton);
    const description = element('p', 'jpm-description',
      '仅列出服务所在 Windows 电脑的 java.exe / javaw.exe。每 3 秒刷新；JDK 可用时补充 Jar / 主类。');
    const warning = element('p', 'jpm-warning',
      '安全限制：仅本机 localhost 可访问。强制结束不会触发目标 JVM 的正常关闭流程，不会自动结束子进程。');
    const status = element('div', 'jpm-status');
    const count = element('span', '', '正在扫描…'); count.id = 'jpm-count';
    const updated = element('span', '', '尚未更新'); updated.id = 'jpm-updated';
    status.append(count, updated);
    const noticeNode = element('p', 'jpm-notice');
    noticeNode.id = 'jpm-notice'; noticeNode.hidden = true;
    const wrap = element('div', 'jpm-table-wrap');
    const table = element('table', 'jpm-table');
    const thead = document.createElement('thead');
    const row = document.createElement('tr');
    ['PID', 'Java 应用 / 可执行文件', '启动时间', '状态', '操作'].forEach(t => row.appendChild(element('th', '', t)));
    thead.appendChild(row);
    const tbody = document.createElement('tbody'); tbody.id = 'jpm-body';
    table.append(thead, tbody); wrap.appendChild(table);
    root.append(description, warning, toolbar, status, noticeNode, wrap);
    updateView();
    refresh();
    timer = window.setInterval(refresh, 3000);
  }
  function unmount() {
    generation++;
    if (timer !== null) window.clearInterval(timer);
    timer = null;
    root = null;
    token = '';
    records = [];
  }
  window.JavaProcessManager = {mount, unmount};
})();
