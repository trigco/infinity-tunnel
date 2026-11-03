// Package tun2socks is a thin gomobile wrapper around the
// xjasonlyu/tun2socks v2 engine: it takes the Android VpnService TUN
// file descriptor and forwards all traffic to a local SOCKS5 proxy.
package tun2socks

import (
	"fmt"
	"time"

	"github.com/xjasonlyu/tun2socks/v2/engine"
)

// Start inserts the engine key and starts forwarding.
// tunFd is the TUN interface fd from VpnService; proxy is a URL such as
// "socks5://127.0.0.1:3080".
func Start(tunFd int, proxy string, mtu int) {
	engine.Insert(&engine.Key{
		MTU:        mtu,
		Device:     fmt.Sprintf("fdbased://%d", tunFd),
		Proxy:      proxy,
		LogLevel:   "info",
		UDPTimeout: 60 * time.Second,
	})
	engine.Start()
}

// Stop halts the engine.
func Stop() {
	engine.Stop()
}
