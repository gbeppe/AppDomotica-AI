// IRRIGATION.1: every 10 s (and right after a command) read the whole OpenSprinkler state.
const pw = flow.get("os_pw");
if (!pw) return null;
return {url: "http://192.168.1.3:9003/ja?pw=" + pw, kind: "poll"};
