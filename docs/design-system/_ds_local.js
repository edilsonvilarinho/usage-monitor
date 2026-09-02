/* Local mount helper.
   When the design-system compiler has produced _ds_bundle.js this file finds the
   published namespace and returns it untouched. Before that (or when a card is
   opened straight from disk) it transpiles the component sources with Babel so
   every card and kit still renders. Cards must not depend on the bundle existing. */
(function () {
  var FILES = [
    'components/core/AppButton.jsx',
    'components/core/AppIconButton.jsx',
    'components/core/AppPanel.jsx',
    'components/core/AppSourceMark.jsx',
    'components/core/AppMetric.jsx',
    'components/forms/AppTextField.jsx',
    'components/forms/AppSwitch.jsx',
    'components/forms/AppTabs.jsx',
    'components/forms/AppSegmentedControl.jsx',
    'components/data/AppProgressTrack.jsx',
    'components/data/AppStatusIndicator.jsx',
    'components/data/AppStatusDot.jsx',
    'components/data/AppDataRow.jsx',
    'components/data/AppDataTable.jsx',
    'components/data/AppColumnHeader.jsx',
    'components/feedback/AppBanner.jsx',
    'components/feedback/AppEmptyState.jsx',
    'components/feedback/AppLoadingState.jsx',
    'components/feedback/AppErrorState.jsx',
    'components/shell/AppWindowFrame.jsx',
    'components/shell/AppStatusBar.jsx',
    'components/shell/AppToolbar.jsx',
    'components/shell/AppUpdateStrip.jsx',
    'components/shell/AppSettingsNav.jsx',
    'components/shell/AppHudBar.jsx'
  ];

  function published() {
    for (var k in window) {
      try {
        var v = window[k];
        if (v && typeof v === 'object' && v.AppPanel && v.AppButton && v.AppStatusIndicator) return v;
      } catch (e) {}
    }
    return null;
  }

  function tryBundle(root) {
    return new Promise(function (resolve) {
      var s = document.createElement('script');
      s.src = root + '_ds_bundle.js';
      s.onload = function () { resolve(true); };
      s.onerror = function () { resolve(false); };
      document.head.appendChild(s);
    });
  }

  window.DSReady = async function (root) {
    root = root || '';
    var found = published();
    if (found) return found;
    await tryBundle(root);
    found = published();
    if (found) return found;
    var ns = {};
    for (var i = 0; i < FILES.length; i++) {
      var url = root + FILES[i];
      var src = await (await fetch(url)).text();
      src = src.replace(/^[\t ]*import[^;]+;[\r\n]*/gm, '').replace(/\bexport\s+function\b/g, 'function');
      var names = [];
      var re = /function\s+([A-Za-z_$][\w$]*)/g, m;
      while ((m = re.exec(src)) !== null) names.push(m[1]);
      var code = Babel.transform(src, { presets: [['react', { runtime: 'classic' }]] }).code;
      var mod = new Function('React', code + '\nreturn {' + names.join(',') + '};')(React);
      Object.assign(ns, mod);
    }
    return ns;
  };
})();
