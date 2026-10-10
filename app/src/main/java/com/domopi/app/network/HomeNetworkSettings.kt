package com.domopi.app.network

/**
 * Home LAN identity used by the Access Path matrix (ported from WallBox UI-TOPBAR.1).
 * Stored only in the app's local DataStore; the matrix rule itself is unchanged:
 * exact Wi-Fi domain for LOCALE, exact VPN route to [subnet] for REMOTO.
 */
data class HomeNetworkSettings(
    val domain: String = DEFAULT_DOMAIN,
    val subnet: String = DEFAULT_SUBNET
) {
    /** Parsed subnet; an invalid stored value matches no route (never a silent default). */
    val parsedSubnet: Ipv4Subnet? get() = Ipv4Subnet.parse(subnet)

    companion object {
        const val DEFAULT_DOMAIN = "casa.zara"
        const val DEFAULT_SUBNET = "192.168.1.0/24"

        private val DNS_LABEL = Regex("^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")

        /** Non-empty, no spaces, DNS name characters (labels of letters, digits, hyphen). */
        fun isValidDomain(value: String): Boolean =
            value.isNotEmpty() && value.length <= 253 && value.split('.').all { DNS_LABEL.matches(it) }

        fun isValidSubnet(value: String): Boolean = Ipv4Subnet.parse(value) != null
    }
}

/** IPv4 CIDR whose address is the network address of its prefix (e.g. 192.168.1.0/24). */
data class Ipv4Subnet(val addressBytes: List<Byte>, val prefixLength: Int) {
    companion object {
        fun parse(value: String): Ipv4Subnet? {
            val parts = value.split('/')
            if (parts.size != 2) return null
            val octets = parts[0].split('.')
            if (octets.size != 4) return null
            val numbers = octets.map { o ->
                if (o.isEmpty() || o.length > 3 || !o.all(Char::isDigit) || (o.length > 1 && o[0] == '0')) return null
                o.toInt().takeIf { it in 0..255 } ?: return null
            }
            val prefixText = parts[1]
            if (prefixText.isEmpty() || prefixText.length > 2 || !prefixText.all(Char::isDigit)) return null
            val prefix = prefixText.toInt().takeIf { it in 0..32 } ?: return null
            val address = numbers.fold(0L) { acc, n -> (acc shl 8) or n.toLong() }
            val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            if (address and mask != address) return null
            return Ipv4Subnet(numbers.map { it.toByte() }, prefix)
        }
    }
}
