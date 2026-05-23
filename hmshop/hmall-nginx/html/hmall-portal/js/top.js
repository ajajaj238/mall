const topApp = {
  template:`
  <div class="top top-upgraded">
    <div class="py-container">
      <div class="shortcut">
        <ul class="fl">
          <li class="f-item welcome-title">星链商城欢迎您！</li>
          <li class="f-item" v-if="!user">
            <a href="/login.html" class="top-link-em">请登录</a>
            <span><a href="#" class="top-link-soft">免费注册</a></span>
          </li>
          <li class="f-item user-entry" v-else>
            欢迎您
            <span class="user-name">{{user.username}}</span>
            <span @click="util.logout()"><a href="#" class="top-link-soft">退出登录</a></span>
          </li>
        </ul>
        <ul class="fr nav-quick-links">
          <li class="f-item"><a href="/" class="top-link-soft">首页</a></li>
          <li class="f-item space"></li>
          <li class="f-item">
            <a href="/cart.html" class="cart-link">
              <span class="cart-mini-icon" aria-hidden="true"></span>
              <span>购物车</span>
            </a>
          </li>
          <li class="f-item space"></li>
          <li class="f-item"><a href="javascript:;" class="top-link-soft">我的星链</a></li>
          <li class="f-item space"></li>
          <li class="f-item"><a href="javascript:;" class="top-link-soft">星链会员</a></li>
          <li class="f-item space"></li>
          <li class="f-item"><a href="javascript:;" class="top-link-soft">企业采购</a></li>
          <li class="f-item space"></li>
          <li class="f-item"><a href="javascript:;" class="top-link-soft">关注星链</a></li>
          <li class="f-item space"></li>
          <li class="f-item"><a href="/ai-chat.html" class="top-link-soft">AI智能助手</a></li>
          <li class="f-item space"></li>
          <li class="f-item"><a href="javascript:;" class="top-link-soft">网站导航</a></li>
        </ul>
      </div>
    </div>
  </div>
  `,
  data(){
    return {
      user: null,
      util
    }
  },
  mounted(){
    this.user = this.util.store.get("user-info")
  },
  methods:{

  },
}

Vue.component("top", topApp);
