package com.chimali.core.security.api

import com.chimali.core.common.annotation.Req

/**
 * Byte-only operations for the current local P-256 child relation.
 *
 * This interface contains no credential-domain dependency, hierarchy/path API, salt evolution,
 * remote KEM, or issuer workflow. Implementations validate canonical point/scalar sizes and ranges.
 */
interface HdkManager {
    /**
     * Derive a child public point and its operation-owned 32-byte factor.
     *
     * [localSecret] and [parentPublicKey] are borrowed and remain caller-owned. The returned
     * [HdkChild.factor] belongs to the caller and must be erased after the corresponding signing
     * operation, or immediately when only the public key is needed.
     */
    @Req("NFR-SEC-041")
    fun deriveChild(
        localSecret: ByteArray,
        parentPublicKey: ByteArray,
        index: UInt,
    ): HdkChild

    /**
     * Combine a borrowed parent scalar and child factor into a new owned child scalar.
     *
     * Both inputs are exactly 32-byte unsigned big-endian scalars in `1..n-1`; neither input is
     * modified. The caller owns and erases the returned 32-byte scalar.
     */
    @Req("NFR-SEC-041")
    fun deriveChildPrivateKey(
        parentPrivateKey: ByteArray,
        factor: ByteArray,
    ): ByteArray
}
