import { defineStore } from "pinia";

export const useUserStore = defineStore("user", {
  state: () => ({
    userId: "",
    nickname: "",
    avatar: "",
    isAdmin: false,
  }),

  getters: {
    isLogin: (state) => !!state.userId,
  },

  actions: {
    setProfile(profile) {
      if (!profile) return;

      if (profile.userId !== undefined) this.userId = profile.userId || "";
      if (profile.nickname !== undefined) this.nickname = profile.nickname || "";
      if (profile.avatar !== undefined) this.avatar = profile.avatar || "";
      if (profile.isAdmin !== undefined) this.isAdmin = !!profile.isAdmin;
    },

    clearUserInfo() {
      this.$reset();
    },
  },
});
