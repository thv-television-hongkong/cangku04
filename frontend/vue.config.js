const path = require('path')

function resolve(dir) {
  return path.join(__dirname, dir)
}

/**
 * 校园公益众筹存证溯源系统 - 前端构建配置
 * 开发环境通过 devServer 代理 /api 到 SpringBoot 后端（默认 8080）
 */
module.exports = {
  publicPath: process.env.VUE_APP_PUBLIC_PATH || '/',
  outputDir: 'dist',
  assetsDir: 'static',
  lintOnSave: false,
  productionSourceMap: false,
  devServer: {
    port: 9528,
    host: '0.0.0.0',
    open: false,
    // API errors are shown by the application message component.  Keeping
    // the webpack overlay hidden prevents a stale-session 401/403 from
    // covering the login page on a LAN device.
    client: {
      overlay: false
    },
    headers: {
      'Cache-Control': 'no-store'
    },
    // Hash routing is used by the SPA, but users may still bookmark
    // /login. Serve index.html for that direct request as well.
    historyApiFallback: {
      disableDotRule: true
    },
    proxy: {
      '/api': {
        target: process.env.VUE_APP_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  configureWebpack: {
    name: '校园公益众筹存证溯源系统',
    resolve: {
      alias: {
        '@': resolve('src')
      }
    }
  },
  chainWebpack(config) {
    config.plugin('html').tap(args => {
      args[0].title = '校园公益众筹存证溯源系统'
      return args
    })
  }
}
