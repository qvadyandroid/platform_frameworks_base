/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net;

import android.annotation.UnsupportedAppUsage;
import android.os.Parcel;
import android.util.Log;
import android.util.Pair;

import java.io.FileDescriptor;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Native methods for managing network interfaces.
 *
 * {@hide}
 */
public class NetworkUtils {

    private static final String TAG = "NetworkUtils";

    /**
     * Attaches a socket filter that accepts DHCP packets to the given socket.
     */
    @UnsupportedAppUsage
    public native static void attachDhcpFilter(FileDescriptor fd) throws SocketException;

    /**
     * Attaches a socket filter that accepts ICMPv6 router advertisements to the given socket.
     * @param fd the socket's {@link FileDescriptor}.
     * @param packetType the hardware address type, one of ARPHRD_*.
     */
    @UnsupportedAppUsage
    public native static void attachRaFilter(FileDescriptor fd, int packetType) throws SocketException;

    /**
     * Attaches a socket filter that accepts L2-L4 signaling traffic required for IP connectivity.
     *
     * This includes: all ARP, ICMPv6 RS/RA/NS/NA messages, and DHCPv4 exchanges.
     *
     * @param fd the socket's {@link FileDescriptor}.
     * @param packetType the hardware address type, one of ARPHRD_*.
     */
    @UnsupportedAppUsage
    public native static void attachControlPacketFilter(FileDescriptor fd, int packetType)
            throws SocketException;

    /**
     * Configures a socket for receiving ICMPv6 router solicitations and sending advertisements.
     * @param fd the socket's {@link FileDescriptor}.
     * @param ifIndex the interface index.
     */
    public native static void setupRaSocket(FileDescriptor fd, int ifIndex) throws SocketException;

    /**
     * Binds the current process to the network designated by {@code netId}.  All sockets created
     * in the future (and not explicitly bound via a bound {@link SocketFactory} (see
     * {@link Network#getSocketFactory}) will be bound to this network.  Note that if this
     * {@code Network} ever disconnects all sockets created in this way will cease to work.  This
     * is by design so an application doesn't accidentally use sockets it thinks are still bound to
     * a particular {@code Network}.  Passing NETID_UNSET clears the binding.
     */
    public native static boolean bindProcessToNetwork(int netId);

    /**
     * Return the netId last passed to {@link #bindProcessToNetwork}, or NETID_UNSET if
     * {@link #unbindProcessToNetwork} has been called since {@link #bindProcessToNetwork}.
     */
    public native static int getBoundNetworkForProcess();

    /**
     * Binds host resolutions performed by this process to the network designated by {@code netId}.
     * {@link #bindProcessToNetwork} takes precedence over this setting.  Passing NETID_UNSET clears
     * the binding.
     *
     * @deprecated This is strictly for legacy usage to support startUsingNetworkFeature().
     */
    @Deprecated
    public native static boolean bindProcessToNetworkForHostResolution(int netId);

    /**
     * Explicitly binds {@code socketfd} to the network designated by {@code netId}.  This
     * overrides any binding via {@link #bindProcessToNetwork}.
     * @return 0 on success or negative errno on failure.
     */
    public native static int bindSocketToNetwork(int socketfd, int netId);

    /**
     * Protect {@code fd} from VPN connections.  After protecting, data sent through
     * this socket will go directly to the underlying network, so its traffic will not be
     * forwarded through the VPN.
     */
    @UnsupportedAppUsage
    public static boolean protectFromVpn(FileDescriptor fd) {
        return protectFromVpn(fd.getInt$());
    }

    /**
     * Protect {@code socketfd} from VPN connections.  After protecting, data sent through
     * this socket will go directly to the underlying network, so its traffic will not be
     * forwarded through the VPN.
     */
    public native static boolean protectFromVpn(int socketfd);

    /**
     * Determine if {@code uid} can access network designated by {@code netId}.
     * @return {@code true} if {@code uid} can access network, {@code false} otherwise.
     */
    public native static boolean queryUserAccess(int uid, int netId);

    /**
     * Add an entry into the ARP cache.
     */
    public static void addArpEntry(Inet4Address ipv4Addr, MacAddress ethAddr, String ifname,
            FileDescriptor fd) throws IOException {
        addArpEntry(ethAddr.toByteArray(), ipv4Addr.getAddress(), ifname, fd);
    }

    private static native void addArpEntry(byte[] ethAddr, byte[] netAddr, String ifname,
            FileDescriptor fd) throws IOException;

    /**
     * @see #intToInet4AddressHTL(int)
     * @deprecated Use either {@link #intToInet4AddressHTH(int)}
     *             or {@link #intToInet4AddressHTL(int)}
     */
    @Deprecated
    @UnsupportedAppUsage
    public static InetAddress intToInetAddress(int hostAddress) {
        return intToInet4AddressHTL(hostAddress);
    }

    /**
     * Convert a IPv4 address from an integer to an InetAddress (0x04030201 -> 1.2.3.4)
     *
     * <p>This method uses the higher-order int bytes as the lower-order IPv4 address bytes,
     * which is an unusual convention. Consider {@link #intToInet4AddressHTH(int)} instead.
     * @param hostAddress an int coding for an IPv4 address, where higher-order int byte is
     *                    lower-order IPv4 address byte
     */
    public static Inet4Address intToInet4AddressHTL(int hostAddress) {
        return intToInet4AddressHTH(Integer.reverseBytes(hostAddress));
    }

    /**
     * Convert a IPv4 address from an integer to an InetAddress (0x01020304 -> 1.2.3.4)
     * @param hostAddress an int coding for an IPv4 address
     */
    public static Inet4Address intToInet4AddressHTH(int hostAddress) {
        byte[] addressBytes = { (byte) (0xff & (hostAddress >> 24)),
                (byte) (0xff & (hostAddress >> 16)),
                (byte) (0xff & (hostAddress >> 8)),
                (byte) (0xff & hostAddress) };

        try {
            return (Inet4Address) InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            throw new AssertionError();
        }
    }

    /**
     * @see #inet4AddressToIntHTL(Inet4Address)
     * @deprecated Use either {@link #inet4AddressToIntHTH(Inet4Address)}
     *             or {@link #inet4AddressToIntHTL(Inet4Address)}
     */
    @Deprecated
    public static int inetAddressToInt(Inet4Address inetAddr)
            throws IllegalArgumentException {
        return inet4AddressToIntHTL(inetAddr);
    }

    /**
     * Convert an IPv4 address from an InetAddress to an integer (1.2.3.4 -> 0x01020304)
     *
     * <p>This conversion can help order IP addresses: considering the ordering
     * 192.0.2.1 < 192.0.2.2 < ..., resulting ints will follow that ordering if read as unsigned
     * integers with {@link Integer#toUnsignedLong}.
     * @param inetAddr is an InetAddress corresponding to the IPv4 address
     * @return the IP address as integer
     */
    public static int inet4AddressToIntHTH(Inet4Address inetAddr)
            throws IllegalArgumentException {
        byte [] addr = inetAddr.getAddress();
        return ((addr[0] & 0xff) << 24) | ((addr[1] & 0xff) << 16)
                | ((addr[2] & 0xff) << 8) | (addr[3] & 0xff);
    }

    /**
     * Convert a IPv4 address from an InetAddress to an integer (1.2.3.4 -> 0x04030201)
     *
     * <p>This method stores the higher-order IPv4 address bytes in the lower-order int bytes,
     * which is an unusual convention. Consider {@link #inet4AddressToIntHTH(Inet4Address)} instead.
     * @param inetAddr is an InetAddress corresponding to the IPv4 address
     * @return the IP address as integer
     */
    public static int inet4AddressToIntHTL(Inet4Address inetAddr) {
        return Integer.reverseBytes(inet4AddressToIntHTH(inetAddr));
    }

    /**
     * @see #prefixLengthToV4NetmaskIntHTL(int)
     * @deprecated Use either {@link #prefixLengthToV4NetmaskIntHTH(int)}
     *             or {@link #prefixLengthToV4NetmaskIntHTL(int)}
     */
    @Deprecated
    @UnsupportedAppUsage
    public static int prefixLengthToNetmaskInt(int prefixLength)
            throws IllegalArgumentException {
        return prefixLengthToV4NetmaskIntHTL(prefixLength);
    }

    /**
     * Convert a network prefix length to an IPv4 netmask integer (prefixLength 17 -> 0xffff8000)
     * @return the IPv4 netmask as an integer
     */
    public static int prefixLengthToV4NetmaskIntHTH(int prefixLength)
            throws IllegalArgumentException {
        if (prefixLength < 0 || prefixLength > 32) {
            throw new IllegalArgumentException("Invalid prefix length (0 <= prefix <= 32)");
        }
        // (int)a << b is equivalent to a << (b & 0x1f): can't shift by 32 (-1 << 32 == -1)
        return prefixLength == 0 ? 0 : 0xffffffff << (32 - prefixLength);
    }

    /**
     * Convert a network prefix length to an IPv4 netmask integer (prefixLength 17 -> 0x0080ffff).
     *
     * <p>This method stores the higher-order IPv4 address bytes in the lower-order int bytes,
     * which is an unusual convention. Consider {@link #prefixLengthToV4NetmaskIntHTH(int)} instead.
     * @return the IPv4 netmask as an integer
     */
    public static int prefixLengthToV4NetmaskIntHTL(int prefixLength)
            throws IllegalArgumentException {
        return Integer.reverseBytes(prefixLengthToV4NetmaskIntHTH(prefixLength));
    }

    /**
     * Convert a IPv4 netmask integer to a prefix length
     * @param netmask as an integer (0xff000000 for a /8 subnet)
     * @return the network prefix length
     */
    public static int netmaskIntToPrefixLength(int netmask) {
        return Integer.bitCount(netmask);
    }

    /**
     * Convert an IPv4 netmask to a prefix length, checking that the netmask is contiguous.
     * @param netmask as a {@code Inet4Address}.
     * @return the network prefix length
     * @throws IllegalArgumentException the specified netmask was not contiguous.
     * @hide
     */
    @UnsupportedAppUsage
    public static int netmaskToPrefixLength(Inet4Address netmask) {
        // inetAddressToInt returns an int in *network* byte order.
        int i = Integer.reverseBytes(inetAddressToInt(netmask));
        int prefixLength = Integer.bitCount(i);
        int trailingZeros = Integer.numberOfTrailingZeros(i);
        if (trailingZeros != 32 - prefixLength) {
            throw new IllegalArgumentException("Non-contiguous netmask: " + Integer.toHexString(i));
        }
        return prefixLength;
    }


    /**
     * Create an InetAddress from a string where the string must be a standard
     * representation of a V4 or V6 address.  Avoids doing a DNS lookup on failure
     * but it will throw an IllegalArgumentException in that case.
     * @param addrString
     * @return the InetAddress
     * @hide
     */
    @UnsupportedAppUsage
    public static InetAddress numericToInetAddress(String addrString)
            throws IllegalArgumentException {
        return InetAddress.parseNumericAddress(addrString);
    }

    /**
     * Writes an InetAddress to a parcel. The address may be null. This is likely faster than
     * calling writeSerializable.
     */
    protected static void parcelInetAddress(Parcel parcel, InetAddress address, int flags) {
        byte[] addressArray = (address != null) ? address.getAddress() : null;
        parcel.writeByteArray(addressArray);
    }

    /**
     * Reads an InetAddress from a parcel. Returns null if the address that was written was null
     * or if the data is invalid.
     */
    protected static InetAddress unparcelInetAddress(Parcel in) {
        byte[] addressArray = in.createByteArray();
        if (addressArray == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(addressArray);
        } catch (UnknownHostException e) {
            return null;
        }
    }


    /**
     *  Masks a raw IP address byte array with the specified prefix length.
     */
    public static void maskRawAddress(byte[] array, int prefixLength) {
        if (prefixLength < 0 || prefixLength > array.length * 8) {
            throw new RuntimeException("IP address with " + array.length +
                    " bytes has invalid prefix length " + prefixLength);
        }

        int offset = prefixLength / 8;
        int remainder = prefixLength % 8;
        byte mask = (byte)(0xFF << (8 - remainder));

        if (offset < array.length) array[offset] = (byte)(array[offset] & mask);

        offset++;

        for (; offset < array.length; offset++) {
            array[offset] = 0;
        }
    }

    /**
     * Get InetAddress masked with prefixLength.  Will never return null.
     * @param address the IP address to mask with
     * @param prefixLength the prefixLength used to mask the IP
     */
    public static InetAddress getNetworkPart(InetAddress address, int prefixLength) {
        byte[] array = address.getAddress();
        maskRawAddress(array, prefixLength);

        InetAddress netPart = null;
        try {
            netPart = InetAddress.getByAddress(array);
        } catch (UnknownHostException e) {
            throw new RuntimeException("getNetworkPart error - " + e.toString());
        }
        return netPart;
    }

    /**
     * Returns the implicit netmask of an IPv4 address, as was the custom before 1993.
     */
    @UnsupportedAppUsage
    public static int getImplicitNetmask(Inet4Address address) {
        int firstByte = address.getAddress()[0] & 0xff;  // Convert to an unsigned value.
        if (firstByte < 128) {
            return 8;
        } else if (firstByte < 192) {
            return 16;
        } else if (firstByte < 224) {
            return 24;
        } else {
            return 32;  // Will likely not end well for other reasons.
        }
    }

    /**
     * Utility method to parse strings such as "192.0.2.5/24" or "2001:db8::cafe:d00d/64".
     * @hide
     */
    public static Pair<InetAddress, Integer> parseIpAndMask(String ipAndMaskString) {
        InetAddress address = null;
        int prefixLength = -1;
        try {
            String[] pieces = ipAndMaskString.split("/", 2);
            prefixLength = Integer.parseInt(pieces[1]);
            address = InetAddress.parseNumericAddress(pieces[0]);
        } catch (NullPointerException e) {            // Null string.
        } catch (ArrayIndexOutOfBoundsException e) {  // No prefix length.
        } catch (NumberFormatException e) {           // Non-numeric prefix.
        } catch (IllegalArgumentException e) {        // Invalid IP address.
        }

        if (address == null || prefixLength == -1) {
            throw new IllegalArgumentException("Invalid IP address and mask " + ipAndMaskString);
        }

        return new Pair<InetAddress, Integer>(address, prefixLength);
    }

    /**
     * Get a prefix mask as Inet4Address for a given prefix length.
     *
     * <p>For example 20 -> 255.255.240.0
     */
    public static Inet4Address getPrefixMaskAsInet4Address(int prefixLength)
            throws IllegalArgumentException {
        return intToInet4AddressHTH(prefixLengthToV4NetmaskIntHTH(prefixLength));
    }

    /**
     * Get the broadcast address for a given prefix.
     *
     * <p>For example 192.168.0.1/24 -> 192.168.0.255
     */
    public static Inet4Address getBroadcastAddress(Inet4Address addr, int prefixLength)
            throws IllegalArgumentException {
        final int intBroadcastAddr = inet4AddressToIntHTH(addr)
                | ~prefixLengthToV4NetmaskIntHTH(prefixLength);
        return intToInet4AddressHTH(intBroadcastAddr);
    }

    /**
     * Check if IP address type is consistent between two InetAddress.
     * @return true if both are the same type.  False otherwise.
     */
    public static boolean addressTypeMatches(InetAddress left, InetAddress right) {
        return (((left instanceof Inet4Address) && (right instanceof Inet4Address)) ||
                ((left instanceof Inet6Address) && (right instanceof Inet6Address)));
    }

    /**
     * Convert a 32 char hex string into a Inet6Address.
     * throws a runtime exception if the string isn't 32 chars, isn't hex or can't be
     * made into an Inet6Address
     * @param addrHexString a 32 character hex string representing an IPv6 addr
     * @return addr an InetAddress representation for the string
     */
    public static InetAddress hexToInet6Address(String addrHexString)
            throws IllegalArgumentException {
        try {
            return numericToInetAddress(String.format(Locale.US, "%s:%s:%s:%s:%s:%s:%s:%s",
                    addrHexString.substring(0,4),   addrHexString.substring(4,8),
                    addrHexString.substring(8,12),  addrHexString.substring(12,16),
                    addrHexString.substring(16,20), addrHexString.substring(20,24),
                    addrHexString.substring(24,28), addrHexString.substring(28,32)));
        } catch (Exception e) {
            Log.e("NetworkUtils", "error in hexToInet6Address(" + addrHexString + "): " + e);
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Create a string array of host addresses from a collection of InetAddresses
     * @param addrs a Collection of InetAddresses
     * @return an array of Strings containing their host addresses
     */
    public static String[] makeStrings(Collection<InetAddress> addrs) {
        String[] result = new String[addrs.size()];
        int i = 0;
        for (InetAddress addr : addrs) {
            result[i++] = addr.getHostAddress();
        }
        return result;
    }

    /**
     * Trim leading zeros from IPv4 address strings
     * Our base libraries will interpret that as octel..
     * Must leave non v4 addresses and host names alone.
     * For example, 192.168.000.010 -> 192.168.0.10
     * TODO - fix base libraries and remove this function
     * @param addr a string representing an ip addr
     * @return a string propertly trimmed
     */
    @UnsupportedAppUsage
    public static String trimV4AddrZeros(String addr) {
        if (addr == null) return null;
        String[] octets = addr.split("\\.");
        if (octets.length != 4) return addr;
        StringBuilder builder = new StringBuilder(16);
        String result = null;
        for (int i = 0; i < 4; i++) {
            try {
                if (octets[i].length() > 3) return addr;
                builder.append(Integer.parseInt(octets[i]));
            } catch (NumberFormatException e) {
                return addr;
            }
            if (i < 3) builder.append('.');
        }
        result = builder.toString();
        return result;
    }

    /**
     * Returns a prefix set without overlaps.
     *
     * This expects the src set to be sorted from shorter to longer. Results are undefined
     * failing this condition. The returned prefix set is sorted in the same order as the
     * passed set, with the same comparator.
     */
    private static TreeSet<IpPrefix> deduplicatePrefixSet(final TreeSet<IpPrefix> src) {
        final TreeSet<IpPrefix> dst = new TreeSet<>(src.comparator());
        // Prefixes match addresses that share their upper part up to their length, therefore
        // the only kind of possible overlap in two prefixes is strict inclusion of the longer
        // (more restrictive) in the shorter (including equivalence if they have the same
        // length).
        // Because prefixes in the src set are sorted from shorter to longer, deduplicating
        // is done by simply iterating in order, and not adding any longer prefix that is
        // already covered by a shorter one.
        newPrefixes:
        for (IpPrefix newPrefix : src) {
            for (IpPrefix existingPrefix : dst) {
                if (existingPrefix.containsPrefix(newPrefix)) {
                    continue newPrefixes;
                }
            }
            dst.add(newPrefix);
        }
        return dst;
    }

    /**
     * Returns how many IPv4 addresses match any of the prefixes in the passed ordered set.
     *
     * Obviously this returns an integral value between 0 and 2**32.
     * The behavior is undefined if any of the prefixes is not an IPv4 prefix or if the
     * set is not ordered smallest prefix to longer prefix.
     *
     * @param prefixes the set of prefixes, ordered by length
     */
    public static long routedIPv4AddressCount(final TreeSet<IpPrefix> prefixes) {
        long routedIPCount = 0;
        for (final IpPrefix prefix : deduplicatePrefixSet(prefixes)) {
            if (!prefix.isIPv4()) {
                Log.wtf(TAG, "Non-IPv4 prefix in routedIPv4AddressCount");
            }
            int rank = 32 - prefix.getPrefixLength();
            routedIPCount += 1L << rank;
        }
        return routedIPCount;
    }

    /**
     * Returns how many IPv6 addresses match any of the prefixes in the passed ordered set.
     *
     * This returns a BigInteger between 0 and 2**128.
     * The behavior is undefined if any of the prefixes is not an IPv6 prefix or if the
     * set is not ordered smallest prefix to longer prefix.
     */
    public static BigInteger routedIPv6AddressCount(final TreeSet<IpPrefix> prefixes) {
        BigInteger routedIPCount = BigInteger.ZERO;
        for (final IpPrefix prefix : deduplicatePrefixSet(prefixes)) {
            if (!prefix.isIPv6()) {
                Log.wtf(TAG, "Non-IPv6 prefix in routedIPv6AddressCount");
            }
            int rank = 128 - prefix.getPrefixLength();
            routedIPCount = routedIPCount.add(BigInteger.ONE.shiftLeft(rank));
        }
        return routedIPCount;
    }
}
