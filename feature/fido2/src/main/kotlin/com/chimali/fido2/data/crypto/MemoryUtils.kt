package com.chimali.fido2.data.crypto

import org.koin.core.annotation.Single

import java.util.Arrays

@Single
class MemoryUtils {
        fun zeroByteArray(array: ByteArray) {
            try {
                Arrays.fill(array, 0.toByte())
            } catch (e: Exception) {
                // Log error but continue
            }
        }

        fun zeroCharArray(array: CharArray) {
            try {
                Arrays.fill(array, '\u0000')
            } catch (e: Exception) {
                // Log error but continue
            }
        }

        fun <T> zeroArray(array: Array<T>) {
            try {
                Arrays.fill(array, null)
            } catch (e: Exception) {
                // Log error but continue
            }
        }

        fun secureStringToChars(input: String): CharArray {
            val chars = input.toCharArray()
            return chars
        }

        fun secureStringToBytes(input: String): ByteArray {
            return input.toByteArray()
        }

        fun clearString(input: String) {
            // Strings are immutable in Java/Kotlin, so we can't zero them directly
            // This method is for consistency and to encourage proper memory management
            // The caller should use secureStringToChars/Bytes and zero those instead
        }

        fun createSecureCopy(source: ByteArray): ByteArray {
            val copy = ByteArray(source.size)
            System.arraycopy(source, 0, copy, 0, source.size)
            return copy
        }

        fun createSecureCopy(source: CharArray): CharArray {
            val copy = CharArray(source.size)
            System.arraycopy(source, 0, copy, 0, source.size)
            return copy
        }

        fun compareSecureArrays(
            a: ByteArray,
            b: ByteArray,
        ): Boolean {
            return if (a.size != b.size) {
                false
            } else {
                var result = 0
                for (i in a.indices) {
                    result = result or (a[i].toInt() xor b[i].toInt())
                }
                result == 0
            }
        }

        fun compareSecureArrays(
            a: CharArray,
            b: CharArray,
        ): Boolean {
            return if (a.size != b.size) {
                false
            } else {
                var result = 0
                for (i in a.indices) {
                    result = result or (a[i].code xor b[i].code)
                }
                result == 0
            }
        }
    }
