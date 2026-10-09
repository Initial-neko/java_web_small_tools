/* ==========================================================================
   SQL 血缘展示器 —— 前端逻辑（Toolbox 内嵌版）
   两个视图：末端表列表（入口） / 单表血缘图（钻取）

   与独立版的差异：
   1. 不再自建 <header>/<main>，而是 mount(container) 注入到 Toolbox 的 .main 里；
   2. 接口返回统一是 ToolResult{success,message,data}，由 api() 拆包；
   3. 所有元素 id 加 lv- 前缀，避免与主页面元素撞名；
   4. 事件用委托绑在 root 上，切走工具时只需 root.innerHTML = '' 即可彻底释放。
   ========================================================================== */

var LineageViewer = (function () {
  'use strict';

  // ---------------------------------------------------------------- 常量

  var API = '/api/lineage-viewer';

  var LAYER_COLOR = {
    ODS: '#9aa4b2',
    DWD: '#4f8ef7',
    DWS: '#8b5cf6',
    ADS: '#ef7a5a',
    DIM: '#38b2ac',
    UNKNOWN: '#cbd5e1'
  };

  var SQL_KEYWORDS = [
    'SELECT', 'FROM', 'WHERE', 'GROUP', 'BY', 'ORDER', 'HAVING', 'INSERT', 'OVERWRITE',
    'INTO', 'TABLE', 'PARTITION', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'FULL', 'JOIN', 'ON',
    'AS', 'AND', 'OR', 'NOT', 'IN', 'EXISTS', 'UNION', 'ALL', 'DISTINCT', 'CASE', 'WHEN',
    'THEN', 'ELSE', 'END', 'WITH', 'CREATE', 'DROP', 'ALTER', 'UPDATE', 'DELETE', 'SET',
    'LIMIT', 'ASC', 'DESC', 'IS', 'NULL', 'BETWEEN', 'LIKE', 'VALUES', 'USING'
  ];
  var SQL_FUNCS = [
    'COUNT', 'SUM', 'MAX', 'MIN', 'AVG', 'ROUND', 'CAST', 'COALESCE', 'NVL',
    'CONCAT', 'SUBSTR', 'SUBSTRING', 'TRIM', 'LOWER', 'UPPER', 'ROW_NUMBER',
    'RANK', 'DENSE_RANK', 'DATE_FORMAT', 'IF', 'NULLIF', 'GREATEST', 'LEAST'
  ];

  // ---------------------------------------------------------------- 状态

  var STATE = {
    root: null,
    scope: 'leaves',
    keyword: '',
    overview: null,
    currentTable: null,
    depth: 6,
    cy: null,
    searchTimer: null,
    toastTimer: null
  };

  // ---------------------------------------------------------------- 工具

  function $(id) { return document.getElementById(id); }

  function esc(s) {
    if (s === null || s === undefined) return '';
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function layerColor(layer) {
    return LAYER_COLOR[layer] || LAYER_COLOR.UNKNOWN;
  }

  function toast(msg) {
    var el = $('lv-toast');
    if (!el) return;
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(STATE.toastTimer);
    STATE.toastTimer = setTimeout(function () { el.classList.remove('show'); }, 2400);
  }

  /** 统一请求：拆开 ToolResult 外壳，失败抛异常，调用方只需处理 data */
  function api(url, options) {
    return fetch(url, options || { headers: { 'Accept': 'application/json' } })
      .then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then(function (body) {
        if (!body || body.success !== true) {
          throw new Error((body && body.message) || '接口返回异常');
        }
        return body.data;
      });
  }

  /** SQL 语法高亮：先转义再按词法着色，避免注入风险 */
  function highlightSql(sql) {
    if (!sql) return '<span class="lv-cmt">-- 该 SQL 未提供正文 --</span>';
    var out = esc(sql);

    // 单行注释与字符串：先占位，避免内部被词法误伤
    var blocks = [];
    out = out.replace(/(--[^\n]*)/g, function (m) {
      blocks.push('<span class="lv-cmt">' + m + '</span>');
      return '\u0000' + (blocks.length - 1) + '\u0000';
    });
    out = out.replace(/('[^'\n]*')/g, function (m) {
      blocks.push('<span class="lv-str">' + m + '</span>');
      return '\u0001' + (blocks.length - 1) + '\u0001';
    });

    // 关键词
    out = out.replace(/\b([A-Za-z_][A-Za-z0-9_]*)\b/g, function (m) {
      var up = m.toUpperCase();
      if (SQL_KEYWORDS.indexOf(up) >= 0) return '<span class="lv-kw">' + m + '</span>';
      if (SQL_FUNCS.indexOf(up) >= 0) return '<span class="lv-fn">' + m + '</span>';
      return m;
    });

    // 数字
    out = out.replace(/\b(\d+)\b/g, '<span class="lv-num">$1</span>');

    // 还原占位
    out = out.replace(/\u0000(\d+)\u0000/g, function (_, i) { return blocks[+i]; });
    out = out.replace(/\u0001(\d+)\u0001/g, function (_, i) { return blocks[+i]; });
    return out;
  }

  // ---------------------------------------------------------------- 骨架

  function renderShell() {
    STATE.root.innerHTML =
      '<header class="lv-topbar">' +
        '<div class="lv-brand" id="lv-brandHome" title="返回末端表列表">' +
          '<span class="lv-brand-mark">◈</span>' +
          '<span class="lv-brand-text">SQL 血缘展示器</span>' +
        '</div>' +
        '<div class="lv-topbar-search" id="lv-topbarSearch" style="display:none">' +
          '<button class="lv-icon-btn" id="lv-btnBack" title="返回列表">←</button>' +
          '<div class="lv-crumbs" id="lv-crumbs"></div>' +
        '</div>' +
        '<div class="lv-topbar-right">' +
          '<span class="lv-stat-inline" id="lv-statInline"></span>' +
        '</div>' +
      '</header>' +

      '<main class="lv-view lv-view-list" id="lv-viewList">' +
        '<section class="lv-hero">' +
          '<h1 class="lv-hero-title">末端产出表</h1>' +
          '<p class="lv-hero-sub">只被写入、没有任何下游的表 —— 血缘链路的终点。' +
            '点击任意一张表，回溯它的完整上游血缘。</p>' +
          '<div class="lv-metrics" id="lv-metrics"></div>' +
        '</section>' +
        '<section class="lv-list-panel">' +
          '<div class="lv-list-toolbar">' +
            '<div class="lv-search-wrap">' +
              '<span class="lv-search-icon">⌕</span>' +
              '<input type="text" id="lv-searchInput" class="lv-search-input" ' +
                'placeholder="搜索表名，例如 ads_order_dashboard …" autocomplete="off" spellcheck="false">' +
              '<button class="lv-search-clear" id="lv-searchClear" style="display:none" title="清空">✕</button>' +
            '</div>' +
            '<div class="lv-scope-tabs" id="lv-scopeTabs">' +
              '<button class="lv-scope-tab active" data-scope="leaves">末端表</button>' +
              '<button class="lv-scope-tab" data-scope="sources">源头表</button>' +
              '<button class="lv-scope-tab" data-scope="all">全部表</button>' +
            '</div>' +
            '<div class="lv-list-count" id="lv-listCount"></div>' +
          '</div>' +
          '<div class="lv-table-grid" id="lv-tableGrid"></div>' +
          '<div class="lv-empty-state" id="lv-listEmpty" style="display:none">' +
            '<div class="lv-empty-icon">∅</div>' +
            '<div class="lv-empty-title">没有匹配的表</div>' +
            '<div class="lv-empty-desc">换个关键词试试，或切换到「全部表」查看中间表。</div>' +
          '</div>' +
        '</section>' +
      '</main>' +

      '<main class="lv-view" id="lv-viewGraph" style="display:none">' +
        '<div class="lv-graph-layout">' +
          '<section class="lv-graph-panel">' +
            '<div class="lv-graph-toolbar">' +
              '<div class="lv-graph-title-group">' +
                '<div class="lv-graph-title" id="lv-graphTitle">—</div>' +
                '<div class="lv-graph-meta" id="lv-graphMeta"></div>' +
              '</div>' +
              '<div class="lv-graph-actions">' +
                '<label class="lv-depth-control">上游层数' +
                  '<select id="lv-depthSelect">' +
                    '<option value="1">1 层</option>' +
                    '<option value="2">2 层</option>' +
                    '<option value="3">3 层</option>' +
                    '<option value="6" selected>6 层</option>' +
                    '<option value="0">全部</option>' +
                  '</select>' +
                '</label>' +
                '<button class="lv-tool-btn" id="lv-btnFit" title="适应画布">适应</button>' +
                '<button class="lv-tool-btn" id="lv-btnReset" title="重置视图">重置</button>' +
                '<button class="lv-tool-btn" id="lv-btnExport" title="导出 PNG">导出</button>' +
              '</div>' +
            '</div>' +
            '<div class="lv-graph-canvas" id="lv-cy"></div>' +
            '<div class="lv-graph-legend">' +
              '<span class="lv-legend-item"><i class="lv-dot" style="background:#9aa4b2"></i>ODS 原始</span>' +
              '<span class="lv-legend-item"><i class="lv-dot" style="background:#4f8ef7"></i>DWD 明细</span>' +
              '<span class="lv-legend-item"><i class="lv-dot" style="background:#8b5cf6"></i>DWS 汇总</span>' +
              '<span class="lv-legend-item"><i class="lv-dot" style="background:#ef7a5a"></i>ADS 应用</span>' +
              '<span class="lv-legend-item"><i class="lv-dot" style="background:#cbd5e1"></i>其他</span>' +
              '<span class="lv-legend-sep"></span>' +
              '<span class="lv-legend-item"><i class="lv-dot ring"></i>当前选中表</span>' +
            '</div>' +
            '<div class="lv-graph-hint">提示：滚轮缩放 · 拖拽平移 · 点击节点继续钻取 · ' +
              '点击连线查看产出该关系的 SQL</div>' +
          '</section>' +
          '<aside class="lv-detail-panel" id="lv-detailPanel">' +
            '<div class="lv-detail-loading" id="lv-detailLoading" style="display:none">加载中…</div>' +
            '<div class="lv-detail-body" id="lv-detailBody"></div>' +
          '</aside>' +
        '</div>' +
      '</main>' +

      '<div class="lv-toast" id="lv-toast"></div>';
  }

  // ---------------------------------------------------------------- 视图切换

  function showList() {
    $('lv-viewList').style.display = '';
    $('lv-viewGraph').style.display = 'none';
    $('lv-topbarSearch').style.display = 'none';
    STATE.currentTable = null;
    if (STATE.cy) { STATE.cy.destroy(); STATE.cy = null; }
  }

  function showGraph() {
    $('lv-viewList').style.display = 'none';
    $('lv-viewGraph').style.display = '';
    $('lv-topbarSearch').style.display = 'flex';
  }

  // ---------------------------------------------------------------- 列表视图

  function loadOverview() {
    return api(API + '/overview').then(function (data) {
      STATE.overview = data;

      var metrics = [
        { v: data.tableCount, l: '表总数' },
        { v: data.edgeCount, l: '血缘关系' },
        { v: data.leafCount, l: '末端产出表' },
        { v: data.sourceCount, l: '源头表' },
        { v: data.clusterCount, l: '独立血缘组' }
      ];
      $('lv-metrics').innerHTML = metrics.map(function (m) {
        return '<div class="lv-metric">' +
          '<div class="lv-metric-value">' + m.v + '</div>' +
          '<div class="lv-metric-label">' + m.l + '</div>' +
          '</div>';
      }).join('');

      $('lv-statInline').textContent = data.tableCount + ' 张表 · ' + data.edgeCount + ' 条关系';
    });
  }

  function loadTableList() {
    var url = API + '/tables/leaves?scope=' + encodeURIComponent(STATE.scope) +
      '&keyword=' + encodeURIComponent(STATE.keyword);

    return api(url).then(function (list) {
      var grid = $('lv-tableGrid');

      if (!list.length) {
        grid.innerHTML = '';
        $('lv-listEmpty').style.display = '';
        $('lv-listCount').textContent = '共 0 张表';
        return;
      }
      $('lv-listEmpty').style.display = 'none';

      var scopeLabel = STATE.scope === 'leaves' ? '末端产出表'
        : STATE.scope === 'sources' ? '源头表' : '全部表';
      $('lv-listCount').textContent = (STATE.keyword ? '匹配 ' : scopeLabel + ' 共 ') +
        list.length + ' 张';

      grid.innerHTML = list.map(function (t) {
        var statHtml;
        if (STATE.scope === 'sources') {
          // 源头表没有产出 SQL（没人写它），看「被多少 SQL 消费」才有意义
          statHtml = '<span>下游 <b>' + t.outDegree + '</b> 张</span>' +
            '<span>被 ' + (t.consumingSqlCount || 0) + ' 条 SQL 消费</span>';
        } else if (STATE.scope === 'all') {
          statHtml = '<span>上游 <b>' + t.inDegree + '</b></span>' +
            '<span>下游 <b>' + t.outDegree + '</b></span>' +
            '<span>' + t.producingSqlCount + ' 条 SQL 产出</span>';
        } else {
          statHtml = '<span>上游 <b>' + t.inDegree + '</b> 张</span>' +
            '<span>' + t.producingSqlCount + ' 条 SQL 产出</span>';
        }

        var tag = t.isLeaf
          ? '<span class="lv-card-leaf-tag">末端</span>'
          : (t.isSource && STATE.scope !== 'leaves'
            ? '<span class="lv-card-leaf-tag" style="color:#1a9e6a;background:#e8f6f0">源头</span>' : '');

        return '<button class="lv-table-card" data-table="' + esc(t.name) + '" ' +
          'style="--lv-layer-color:' + layerColor(t.layer) + '">' +
          tag +
          '<div class="lv-card-head">' +
          '<span class="lv-layer-badge ' + esc(t.layer) + '">' + esc(t.layer) + '</span>' +
          '<span class="lv-card-name" title="' + esc(t.name) + '">' + esc(t.shortName) + '</span>' +
          '</div>' +
          '<div class="lv-card-stats">' + statHtml + '</div>' +
          '</button>';
      }).join('');
    });
  }

  // ---------------------------------------------------------------- 图视图

  function openTable(tableName) {
    STATE.currentTable = tableName;
    showGraph();

    $('lv-crumbs').innerHTML =
      '<span>末端表</span><span class="lv-crumb-sep">/</span>' +
      '<span class="lv-crumb-current" title="' + esc(tableName) + '">' + esc(tableName) + '</span>';

    $('lv-detailLoading').style.display = '';
    $('lv-detailBody').innerHTML = '';

    Promise.all([
      loadLineageGraph(tableName),
      loadTableDetail(tableName)
    ]).then(function () {
      $('lv-detailLoading').style.display = 'none';
    }).catch(function (e) {
      $('lv-detailLoading').style.display = 'none';
      toast('加载失败：' + e.message);
    });
  }

  function loadLineageGraph(tableName) {
    var url = API + '/tables/' + encodeURIComponent(tableName) + '/lineage?depth=' + STATE.depth;

    return api(url).then(function (data) {
      $('lv-graphTitle').textContent = data.focus;
      $('lv-graphMeta').textContent = data.nodeCount + ' 个节点 · ' + data.edgeCount +
        ' 条关系 · 上游最长 ' + data.maxUpstreamDepth + ' 跳';

      renderGraph(data);
    });
  }

  /** 按「到焦点表的跳数」分层排布：上游在左、焦点居中、下游在右 */
  function computePositions(data) {
    var byDist = {};
    data.nodes.forEach(function (n) {
      var d = n.distance;
      if (!byDist[d]) byDist[d] = [];
      byDist[d].push(n);
    });

    var dists = Object.keys(byDist).map(Number).sort(function (a, b) { return a - b; });
    var X_GAP = 250;
    var Y_GAP = 78;
    var positions = {};

    // 上游跳数越大越靠左；焦点 0；下游 -1 靠右
    var upstreamDists = dists.filter(function (d) { return d >= 0; });
    var maxUp = upstreamDists.length ? Math.max.apply(null, upstreamDists) : 0;

    dists.forEach(function (d) {
      var colX;
      if (d < 0) {
        colX = (maxUp + 1) * X_GAP;      // 下游列
      } else {
        colX = (maxUp - d) * X_GAP;      // 上游：跳数大 → 靠左
      }
      var group = byDist[d];
      // 按层+名字排序，让同层表聚在一起，观感更整齐
      group.sort(function (a, b) {
        if (a.layer !== b.layer) return String(a.layer).localeCompare(String(b.layer));
        return String(a.name).localeCompare(String(b.name));
      });
      var totalH = (group.length - 1) * Y_GAP;
      group.forEach(function (n, i) {
        positions[n.name] = { x: colX, y: i * Y_GAP - totalH / 2 };
      });
    });

    return positions;
  }

  function renderGraph(data) {
    if (STATE.cy) { STATE.cy.destroy(); STATE.cy = null; }
    if (typeof cytoscape !== 'function') {
      $('lv-cy').innerHTML =
        '<div style="padding:30px;color:#e74c3c;">图形库未加载成功，请检查 ' +
        '/lineage-viewer/vendor/cytoscape.min.js 是否可访问。</div>';
      return;
    }

    var positions = computePositions(data);
    var elements = [];

    data.nodes.forEach(function (n) {
      elements.push({
        group: 'nodes',
        data: {
          id: n.name,
          label: n.shortName,
          fullName: n.name,
          layer: n.layer,
          inDegree: n.inDegree,
          outDegree: n.outDegree,
          isFocus: !!n.isFocus,
          direction: n.direction,
          color: layerColor(n.layer)
        },
        position: positions[n.name] || { x: 0, y: 0 }
      });
    });

    data.edges.forEach(function (e) {
      elements.push({
        group: 'edges',
        data: {
          id: e.id,
          source: e.source,
          target: e.target,
          weight: e.weight,
          sqlCount: e.sqlCount,
          sqlNames: e.sqlNames
        }
      });
    });

    STATE.cy = cytoscape({
      container: $('lv-cy'),
      elements: elements,
      wheelSensitivity: 0.22,
      minZoom: 0.15,
      maxZoom: 2.5,
      style: [
        {
          selector: 'node',
          style: {
            'shape': 'round-rectangle',
            'width': 'label',
            'height': 30,
            'padding': '9px',
            'background-color': '#ffffff',
            'border-width': 2,
            'border-color': function (ele) { return ele.data('color'); },
            'label': 'data(label)',
            'font-family': 'ui-monospace, Menlo, Consolas, monospace',
            'font-size': 11.5,
            'color': '#1f2329',
            'text-valign': 'center',
            'text-halign': 'center',
            'text-wrap': 'none',
            'overlay-opacity': 0,
            'transition-property': 'border-color, border-width, background-color',
            'transition-duration': '150ms'
          }
        },
        {
          selector: 'node[direction = "upstream"]',
          style: { 'background-color': '#fbfcfe' }
        },
        {
          selector: 'node[direction = "downstream"]',
          style: { 'background-color': '#f2f5f9', 'border-style': 'dashed' }
        },
        {
          selector: 'node[?isFocus]',
          style: {
            'background-color': '#fdf0ec',
            'border-color': '#ef7a5a',
            'border-width': 3,
            'font-weight': 'bold',
            'font-size': 12.5,
            'color': '#c2410c'
          }
        },
        {
          selector: 'node:selected',
          style: {
            'border-width': 3,
            'border-color': '#2f6feb',
            'background-color': '#eaf1fe'
          }
        },
        {
          selector: 'edge',
          style: {
            'width': function (ele) {
              var w = ele.data('weight') || 1;
              return Math.min(3.2, 1.2 + (w - 1) * 0.7);
            },
            'line-color': '#c3cad4',
            'target-arrow-color': '#c3cad4',
            'target-arrow-shape': 'triangle',
            'arrow-scale': 0.85,
            'curve-style': 'bezier',
            'opacity': 0.9,
            'overlay-opacity': 0,
            'transition-property': 'line-color, target-arrow-color, width',
            'transition-duration': '150ms'
          }
        },
        {
          selector: 'edge:selected',
          style: { 'line-color': '#2f6feb', 'target-arrow-color': '#2f6feb', 'width': 2.8 }
        },
        {
          // 与焦点或选中节点直接相连的边，高亮
          selector: 'edge.hl',
          style: { 'line-color': '#ef7a5a', 'target-arrow-color': '#ef7a5a', 'width': 2.4 }
        },
        { selector: 'node.dim', style: { 'opacity': 0.42 } },
        {
          // 缩放过小时隐藏标签，避免文字糊成一片
          selector: 'node.nolabel',
          style: { 'label': '' }
        }
      ],
      layout: { name: 'preset', fit: true, padding: 50 }
    });

    // 事件：点节点继续钻取
    STATE.cy.on('tap', 'node', function (evt) {
      var name = evt.target.data('fullName');
      if (name === STATE.currentTable) {
        loadTableDetail(name);
        return;
      }
      openTable(name);
    });

    // 事件：点连线查看产出该关系的 SQL
    STATE.cy.on('tap', 'edge', function (evt) {
      var d = evt.target.data();
      loadEdgeDetail(d.source, d.target);
    });

    // 点空白处：取消选中，恢复高亮状态
    STATE.cy.on('tap', function (evt) {
      if (evt.target === STATE.cy) {
        STATE.cy.elements().removeClass('hl dim');
      }
    });

    // 缩放级别联动标签显隐
    STATE.cy.on('zoom', applyLevelOfDetail);

    STATE.cy.ready(function () {
      fitGraph();
      highlightAroundFocus();
      applyLevelOfDetail();
    });
  }

  /**
   * 自动适配画布，但不低于「可读缩放」。
   * 节点一多，cytoscape 的默认 fit 会把整图压缩到看不清文字。
   * 这里给一个缩放下限：超出就让用户平移浏览，而不是糊成一团。
   */
  function fitGraph() {
    var cy = STATE.cy;
    if (!cy || cy.elements().length === 0) return;

    var MIN_READABLE_ZOOM = 0.62;
    cy.fit(undefined, 60);

    if (cy.zoom() < MIN_READABLE_ZOOM) {
      cy.zoom({ level: MIN_READABLE_ZOOM, renderedPosition: { x: cy.width() / 2, y: cy.height() / 2 } });
      var focus = cy.getElementById(STATE.currentTable);
      if (focus && focus.length) {
        cy.center(focus);
      } else {
        cy.center();
      }
    }
  }

  /**
   * 分级显示：缩放过小时隐藏标签，只留选中表与直接相邻节点，
   * 避免 60+ 节点的图变成一片文字糊。
   */
  function applyLevelOfDetail() {
    var cy = STATE.cy;
    if (!cy) return;
    var showLabels = cy.zoom() >= 0.55;

    if (showLabels) {
      cy.nodes().removeClass('nolabel');
    } else {
      var focusId = STATE.currentTable;
      cy.nodes().forEach(function (n) {
        var id = n.id();
        var keep = id === focusId;
        if (!keep) {
          cy.edges().forEach(function (e) {
            if ((e.data('source') === focusId && e.data('target') === id) ||
                (e.data('target') === focusId && e.data('source') === id)) {
              keep = true;
            }
          });
        }
        n.toggleClass('nolabel', !keep);
      });
    }
    cy.style().update();
  }

  /** 把与焦点表直接相关的边加粗着色，让主线一眼可辨 */
  function highlightAroundFocus() {
    var cy = STATE.cy;
    if (!cy) return;
    var focus = STATE.currentTable;
    cy.edges().forEach(function (e) {
      if (e.data('source') === focus || e.data('target') === focus) {
        e.addClass('hl');
      }
    });
  }

  // ---------------------------------------------------------------- 详情侧栏

  function loadTableDetail(tableName) {
    return api(API + '/tables/' + encodeURIComponent(tableName)).then(function (d) {
      renderTableDetail(d);
    });
  }

  function renderTableDetail(d) {
    var tags = '';
    if (d.isLeaf) tags += '<span class="lv-tag leaf">末端产出表</span>';
    if (d.isSource) tags += '<span class="lv-tag source">源头表</span>';
    if (d.name === STATE.currentTable) tags += '<span class="lv-tag focus">当前</span>';

    function chips(list, emptyText) {
      if (!list || !list.length) {
        return '<span class="lv-kv-val"><span class="lv-none">' + emptyText + '</span></span>';
      }
      return '<div class="lv-chip-list">' + list.map(function (t) {
        return '<span class="lv-chip" data-goto="' + esc(t) + '" title="查看 ' + esc(t) + ' 的血缘">' +
          esc(t) + '</span>';
      }).join('') + '</div>';
    }

    var html = '';

    html += '<div class="lv-detail-head">' +
      '<div class="lv-detail-kind">数据表</div>' +
      '<div class="lv-detail-name">' + esc(d.name) + '</div>' +
      '<div class="lv-detail-sub">' +
      '<span class="lv-layer-badge ' + esc(d.layer) + '" style="--lv-layer-color:' +
      layerColor(d.layer) + '">' + esc(d.layer) + '</span>' + tags +
      '</div></div>';

    html += '<div class="lv-kv"><div class="lv-kv-key">分层</div><div class="lv-kv-val">' +
      esc(d.layer) + '</div></div>';
    html += '<div class="lv-kv"><div class="lv-kv-key">上游表数</div><div class="lv-kv-val">' +
      d.inDegree + ' 张</div></div>';
    html += '<div class="lv-kv"><div class="lv-kv-key">下游表数</div><div class="lv-kv-val">' +
      d.outDegree + ' 张</div></div>';
    html += '<div class="lv-kv"><div class="lv-kv-key">产出 SQL</div><div class="lv-kv-val">' +
      (d.producerCount ? d.producerCount + ' 条' : '<span class="lv-none">无（外部接入表）</span>') +
      '</div></div>';
    html += '<div class="lv-kv"><div class="lv-kv-key">消费 SQL</div><div class="lv-kv-val">' +
      (d.consumerCount ? d.consumerCount + ' 条' : '<span class="lv-none">无</span>') + '</div></div>';
    html += '<div class="lv-kv"><div class="lv-kv-key">血缘组</div><div class="lv-kv-val">#' +
      d.clusterId + '</div></div>';

    html += '<div class="lv-section"><div class="lv-section-title">直接上游表' +
      '<span class="lv-count-pill">' + d.upstreamTables.length + '</span></div>' +
      chips(d.upstreamTables, '没有上游，这是源头表') + '</div>';

    html += '<div class="lv-section"><div class="lv-section-title">直接下游表' +
      '<span class="lv-count-pill">' + d.downstreamTables.length + '</span></div>' +
      chips(d.downstreamTables, '没有下游，这是末端产出表') + '</div>';

    // 产出该表的 SQL —— 完整字段
    html += '<div class="lv-section"><div class="lv-section-title">产出该表的 SQL' +
      '<span class="lv-count-pill">' + d.producers.length + '</span></div>';

    if (!d.producers.length) {
      html += '<div style="font-size:12.5px;color:var(--lv-text-3);line-height:1.6">' +
        '该表没有产出作业 —— 它是从外部系统接入的原始表（源头表）。' +
        '它的血缘体现在下游：有 ' + d.consumerCount + ' 条 SQL 读取它。</div>';
    } else {
      html += d.producers.map(function (s, i) {
        return sqlCardHtml(s, 'p' + i, true);
      }).join('');
    }
    html += '</div>';

    $('lv-detailBody').innerHTML = html;

    // chip 跳转：委托在 lv-detailBody 上，随内容重建自动生效
    $('lv-detailBody').querySelectorAll('[data-goto]').forEach(function (el) {
      el.addEventListener('click', function () { openTable(el.getAttribute('data-goto')); });
    });

    bindSqlCards($('lv-detailBody'));
  }

  /** 边详情：这条关系由哪些 SQL 产出 */
  function loadEdgeDetail(from, to) {
    $('lv-detailLoading').style.display = 'none';

    api(API + '/edges/detail?from=' + encodeURIComponent(from) + '&to=' + encodeURIComponent(to))
      .then(function (d) {
        var html = '';

        html += '<div class="lv-detail-head">' +
          '<div class="lv-detail-kind">血缘关系</div>' +
          '<div class="lv-detail-name" style="font-size:12.5px">' + esc(from) + '</div>' +
          '<div style="text-align:center;color:var(--lv-focus);font-size:15px;margin:3px 0">↓</div>' +
          '<div class="lv-detail-name" style="font-size:12.5px">' + esc(to) + '</div>' +
          '<div class="lv-detail-sub"><span class="lv-tag focus">' + d.weight +
          ' 条 SQL 造成此关系</span></div>' +
          '</div>';

        html += '<div class="lv-section"><div class="lv-section-title">产出该关系的 SQL' +
          '<span class="lv-count-pill">' + d.sqls.length + '</span></div>';

        if (!d.sqls.length) {
          html += '<div style="font-size:12.5px;color:var(--lv-text-3)">未找到对应的 SQL 记录。</div>';
        } else {
          html += d.sqls.map(function (s, i) {
            return sqlCardHtml(s, 'e' + i, true);
          }).join('');
        }
        html += '</div>';

        $('lv-detailBody').innerHTML = html;
        bindSqlCards($('lv-detailBody'));

        // 高亮这条边
        if (STATE.cy) {
          var e = STATE.cy.getElementById(from + '->' + to);
          if (e && e.length) {
            STATE.cy.elements().unselect();
            e.select();
          }
        }
      })
      .catch(function (err) { toast('加载失败：' + err.message); });
  }

  /** SQL 卡片：默认折叠，点开显示 description 与 SQL 正文 */
  function sqlCardHtml(s, uid, openByDefault) {
    var inList = (s.inputTables && s.inputTables.length) ? s.inputTables.join(' , ') : '—';
    var outList = (s.outputTables && s.outputTables.length) ? s.outputTables.join(' , ') : '—';

    return '<div class="lv-sql-card' + (openByDefault ? ' open' : '') + '" data-uid="' + uid + '">' +
      '<div class="lv-sql-card-head">' +
      '<span class="lv-sql-toggle">▶</span>' +
      '<div class="lv-sql-card-title">' +
      '<div class="lv-sql-name">' + esc(s.name || '(未命名)') + '</div>' +
      '<div class="lv-sql-source">来源：' + esc(s.source || '未知') + '</div>' +
      '</div>' +
      '</div>' +
      '<div class="lv-sql-card-body">' +
      (s.description
        ? '<div class="lv-sql-desc">' + esc(s.description) + '</div>'
        : '<div class="lv-sql-desc" style="color:var(--lv-text-3)">（无描述）</div>') +
      '<div class="lv-sql-tables">' +
      '<div class="lv-sql-tables-row"><span class="lv-lbl">输入表</span><span class="lv-val">' +
      esc(inList) + '</span></div>' +
      '<div class="lv-sql-tables-row"><span class="lv-lbl">输出表</span><span class="lv-val">' +
      esc(outList) + '</span></div>' +
      '</div>' +
      '<pre class="lv-sql-code">' + highlightSql(s.sql) + '</pre>' +
      '</div></div>';
  }

  function bindSqlCards(root) {
    root.querySelectorAll('.lv-sql-card-head').forEach(function (head) {
      head.addEventListener('click', function () {
        head.parentElement.classList.toggle('open');
      });
    });
  }

  // ---------------------------------------------------------------- 事件绑定

  function bindEvents() {
    var root = STATE.root;

    // 品牌名 → 回列表
    $('lv-brandHome').addEventListener('click', function () { showList(); loadTableList(); });
    $('lv-btnBack').addEventListener('click', function () { showList(); loadTableList(); });

    // 搜索框（防抖）
    var input = $('lv-searchInput');
    input.addEventListener('input', function () {
      STATE.keyword = input.value;
      $('lv-searchClear').style.display = input.value ? '' : 'none';
      clearTimeout(STATE.searchTimer);
      STATE.searchTimer = setTimeout(loadTableList, 180);
    });
    input.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        input.value = '';
        STATE.keyword = '';
        $('lv-searchClear').style.display = 'none';
        loadTableList();
      }
    });
    $('lv-searchClear').addEventListener('click', function () {
      input.value = '';
      STATE.keyword = '';
      $('lv-searchClear').style.display = 'none';
      input.focus();
      loadTableList();
    });

    // 范围切页
    $('lv-scopeTabs').addEventListener('click', function (e) {
      var btn = e.target.closest('.lv-scope-tab');
      if (!btn) return;
      $('lv-scopeTabs').querySelectorAll('.lv-scope-tab').forEach(function (b) {
        b.classList.toggle('active', b === btn);
      });
      STATE.scope = btn.getAttribute('data-scope');
      loadTableList();
    });

    // 卡片点击 → 进入血缘图（事件委托）
    $('lv-tableGrid').addEventListener('click', function (e) {
      var card = e.target.closest('.lv-table-card');
      if (card) openTable(card.getAttribute('data-table'));
    });

    // 深度切换
    $('lv-depthSelect').addEventListener('change', function () {
      STATE.depth = parseInt(this.value, 10);
      if (STATE.currentTable) loadLineageGraph(STATE.currentTable);
    });

    // 图操作
    $('lv-btnFit').addEventListener('click', function () {
      fitGraph();
      applyLevelOfDetail();
    });
    $('lv-btnReset').addEventListener('click', function () {
      if (STATE.currentTable) loadLineageGraph(STATE.currentTable);
    });
    $('lv-btnExport').addEventListener('click', function () {
      if (!STATE.cy) return;
      try {
        var png = STATE.cy.png({ full: true, scale: 2, bg: '#ffffff' });
        var a = document.createElement('a');
        a.href = png;
        a.download = (STATE.currentTable || 'lineage') + '-lineage.png';
        a.click();
        toast('已导出 PNG');
      } catch (err) {
        toast('导出失败：' + err.message);
      }
    });

    // 键盘快捷键只在本工具处于前台时生效：
    // 主页面（另一个 input 正在输入）时不应被劫持
    root.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && STATE.currentTable) {
        showList();
        loadTableList();
      }
    });
  }

  // ---------------------------------------------------------------- 生命周期

  /** 挂载到 Toolbox 的 .main 容器 */
  function mount(container) {
    unmount();

    var root = document.createElement('div');
    root.className = 'lv-root';
    root.id = 'lv-root';
    container.innerHTML = '';
    container.appendChild(root);

    STATE.root = root;
    STATE.scope = 'leaves';
    STATE.keyword = '';
    STATE.currentTable = null;
    STATE.depth = 6;

    renderShell();
    bindEvents();

    loadOverview().catch(function (e) { toast('统计加载失败：' + e.message); });
    loadTableList().catch(function (e) { toast('列表加载失败：' + e.message); });
  }

  /** 卸载：销毁图实例、清空容器。留着内存里的 cytoscape 会让切走再切回时画布错乱。 */
  function unmount() {
    if (STATE.cy) {
      try { STATE.cy.destroy(); } catch (e) { /* 忽略销毁异常 */ }
      STATE.cy = null;
    }
    clearTimeout(STATE.searchTimer);
    clearTimeout(STATE.toastTimer);
    STATE.root = null;
  }

  return { mount: mount, unmount: unmount };
})();
