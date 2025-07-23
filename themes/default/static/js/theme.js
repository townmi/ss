// Default Theme JavaScript

(function () {
  'use strict';

  // 初始化主题
  function initTheme() {
    console.log('Default theme initialized');

    // 处理下拉菜单
    initDropdowns();

    // 处理移动端菜单
    initMobileMenu();

    // 处理主题切换
    initThemeSwitcher();
  }

  // 初始化下拉菜单
  function initDropdowns() {
    const dropdowns = document.querySelectorAll('.has-dropdown');

    dropdowns.forEach(dropdown => {
      const link = dropdown.querySelector('.navbar-link');
      const menu = dropdown.querySelector('.navbar-dropdown');

      if (link && menu) {
        link.addEventListener('click', (e) => {
          e.preventDefault();
          dropdown.classList.toggle('is-active');
        });

        // 点击外部关闭下拉菜单
        document.addEventListener('click', (e) => {
          if (!dropdown.contains(e.target)) {
            dropdown.classList.remove('is-active');
          }
        });
      }
    });
  }

  // 初始化移动端菜单
  function initMobileMenu() {
    const burger = document.querySelector('.navbar-burger');
    const menu = document.querySelector('.navbar-menu');

    if (burger && menu) {
      burger.addEventListener('click', () => {
        burger.classList.toggle('is-active');
        menu.classList.toggle('is-active');
      });
    }
  }

  // 初始化主题切换器
  function initThemeSwitcher() {
    // 监听主题切换事件
    window.addEventListener('theme-switched', (e) => {
      console.log('Theme switched to:', e.detail.theme);
      // 可以在这里添加主题切换的动画效果
    });
  }

  // 工具函数：发送 API 请求
  window.apiRequest = function (url, options = {}) {
    const defaultOptions = {
      headers: {
        'Content-Type': 'application/json',
      },
    };

    return fetch(url, { ...defaultOptions, ...options })
      .then(response => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.json();
      });
  };

  // 工具函数：显示通知
  window.showNotification = function (message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `alert alert-${type}`;
    notification.textContent = message;

    const container = document.querySelector('.page-content') || document.body;
    container.insertBefore(notification, container.firstChild);

    // 3秒后自动移除
    setTimeout(() => {
      notification.remove();
    }, 3000);
  };

  // DOM 加载完成后初始化
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initTheme);
  } else {
    initTheme();
  }

})(); 