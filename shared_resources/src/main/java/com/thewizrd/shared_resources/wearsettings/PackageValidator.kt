/*
 * Copyright 2018 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thewizrd.shared_resources.wearsettings

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.os.Build
import android.util.Base64
import com.thewizrd.shared_resources.R
import org.xmlpull.v1.XmlPullParserException
import timber.log.Timber
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Validates that the calling package is authorized to access a service
 *
 * The list of allowed signing certificates and their corresponding package names is defined in
 * res/xml/allowed_wearsettings_callers.xml.
 *
 * Based on PackageValidator used for MediaBrowserServices
 *
 * Reference:
 * https://github.com/android/uamp/blob/main/common/src/main/java/com/example/android/uamp/media/PackageValidator.kt
 * https://github.com/android/uamp/blob/main/common/src/main/res/xml/allowed_media_browser_callers.xml
 */
class PackageValidator(context: Context) {
    private val context: Context
    private val packageManager: PackageManager

    private val certificateAllowList: Map<String, KnownCallerInfo>
    private val platformSignature: String

    private val callerChecked = mutableMapOf<String, Boolean>()

    init {
        val parser = context.resources.getXml(R.xml.allowed_wearsettings_callers)
        this.context = context.applicationContext
        this.packageManager = this.context.packageManager

        certificateAllowList = buildCertificateAllowList(parser)
        platformSignature = getSystemSignature()
    }

    /**
     * Checks whether the caller attempting to connect to a service is known.
     *
     * @param callingPackage The package name of the caller.
     * @return `true` if the caller is known, `false` otherwise.
     */
    fun isKnownCaller(callingPackage: String): Boolean {
        // If the caller has already been checked, return the previous result here.
        if (callerChecked[callingPackage] == true) {
            return true
        }

        /**
         * Because some of these checks can be slow, we save the results in [callerChecked] after
         * this code is run.
         *
         * In particular, there's little reason to recompute the calling package's certificate
         * signature (SHA-256) each call.
         *
         * This is safe to do as we know the UID matches the package's UID (from the check above),
         * and app UIDs are set at install time. Additionally, a package name + UID is guaranteed to
         * be constant until a reboot. (After a reboot then a previously assigned UID could be
         * reassigned.)
         */

        // Build the caller info for the rest of the checks here.
        val callerPackageInfo = buildCallerInfo(callingPackage)
            ?: throw IllegalStateException("Caller wasn't found in the system?")

        val callerSignature = callerPackageInfo.signature
        val isPackageInAllowList = certificateAllowList[callingPackage]?.signatures?.first {
            it.signature == callerSignature
        } != null

        val isCallerKnown = when {
            // If it's one of the apps on the allow list, allow it.
            isPackageInAllowList -> true
            // If none of the previous checks succeeded, then the caller is unrecognized.
            else -> false
        }

        // Save our work for next time.
        callerChecked[callingPackage] = isCallerKnown
        return isCallerKnown
    }

    /**
     * Builds a [CallerPackageInfo] for a given package that can be used for all the
     * various checks that are performed before allowing an app to connect to a
     * service
     */
    private fun buildCallerInfo(callingPackage: String): CallerPackageInfo? {
        val packageInfo = getPackageInfo(callingPackage) ?: return null
        val appInfo = packageInfo.applicationInfo ?: return null

        val appName = appInfo.loadLabel(packageManager).toString()
        val uid = appInfo.uid
        val signature = getSignature(packageInfo)

        return CallerPackageInfo(appName, callingPackage, uid, signature)
    }

    /**
     * Looks up the [PackageInfo] for a package name.
     * This requests both the signatures (for checking if an app is on the allow list) and
     * the app's permissions, which allow for more flexibility in the allow list.
     *
     * @return [PackageInfo] for the package name or null if it's not found.
     */
    @SuppressLint("PackageManagerGetSignatures")
    private fun getPackageInfo(callingPackage: String): PackageInfo? {
        val signatureFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

        return packageManager.getPackageInfo(
            callingPackage,
            signatureFlag or PackageManager.GET_PERMISSIONS
        )
    }

    /**
     * Gets the signature of a given package's [PackageInfo].
     *
     * The "signature" is a SHA-256 hash of the public key of the signing certificate used by
     * the app.
     *
     * If the app is not found, or if the app does not have exactly one signature, this method
     * returns `null` as the signature.
     */
    private fun getSignature(packageInfo: PackageInfo): String? {
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            packageInfo.signatures
        }?.let { signatures ->
            if (signatures.size != 1) {
                // Security best practices dictate that an app should be signed with exactly one (1)
                // signature. Because of this, if there are multiple signatures, reject it.
                null
            } else {
                signatures[0]
            }
        }

        if (signature != null) {
            val certificate = signature.toByteArray()
            return getSignatureSha256(certificate)
        } else {
            return null
        }
    }

    private fun buildCertificateAllowList(parser: XmlResourceParser): Map<String, KnownCallerInfo> {

        val certificateAllowList = LinkedHashMap<String, KnownCallerInfo>()
        try {
            var eventType = parser.next()
            while (eventType != XmlResourceParser.END_DOCUMENT) {
                if (eventType == XmlResourceParser.START_TAG) {
                    val callerInfo = when (parser.name) {
                        "signing_certificate" -> parseV1Tag(parser)
                        "signature" -> parseV2Tag(parser)
                        else -> null
                    }

                    callerInfo?.let { info ->
                        val packageName = info.packageName
                        val existingCallerInfo = certificateAllowList[packageName]
                        if (existingCallerInfo != null) {
                            existingCallerInfo.signatures += callerInfo.signatures
                        } else {
                            certificateAllowList[packageName] = callerInfo
                        }
                    }
                }

                eventType = parser.next()
            }
        } catch (xmlException: XmlPullParserException) {
            Timber.e(xmlException, "Could not read allowed callers from XML.")
        } catch (ioException: IOException) {
            Timber.e(ioException, "Could not read allowed callers from XML.")
        }

        return certificateAllowList
    }

    /**
     * Parses a v1 format tag. See allowed_media_browser_callers.xml for more details.
     */
    private fun parseV1Tag(parser: XmlResourceParser): KnownCallerInfo {
        val name = parser.getAttributeValue(null, "name")
        val packageName = parser.getAttributeValue(null, "package")
        val isRelease = parser.getAttributeBooleanValue(null, "release", false)
        val certificate = parser.nextText().replace(WHITESPACE_REGEX, "")
        val signature = getSignatureSha256(certificate)

        val callerSignature = KnownSignature(signature, isRelease)
        return KnownCallerInfo(name, packageName, mutableSetOf(callerSignature))
    }

    /**
     * Parses a v2 format tag. See allowed_media_browser_callers.xml for more details.
     */
    private fun parseV2Tag(parser: XmlResourceParser): KnownCallerInfo {
        val name = parser.getAttributeValue(null, "name")
        val packageName = parser.getAttributeValue(null, "package")

        val callerSignatures = mutableSetOf<KnownSignature>()
        var eventType = parser.next()
        while (eventType != XmlResourceParser.END_TAG) {
            val isRelease = parser.getAttributeBooleanValue(null, "release", false)
            val signature = parser.nextText().replace(WHITESPACE_REGEX, "").lowercase()
            callerSignatures += KnownSignature(signature, isRelease)

            eventType = parser.next()
        }

        return KnownCallerInfo(name, packageName, callerSignatures)
    }

    /**
     * Finds the Android platform signing key signature. This key is never null.
     */
    private fun getSystemSignature(): String =
        getPackageInfo(ANDROID_PLATFORM)?.let { platformInfo ->
            getSignature(platformInfo)
        } ?: throw IllegalStateException("Platform signature not found")

    /**
     * Creates a SHA-256 signature given a Base64 encoded certificate.
     */
    private fun getSignatureSha256(certificate: String): String {
        return getSignatureSha256(Base64.decode(certificate, Base64.DEFAULT))
    }

    /**
     * Creates a SHA-256 signature given a certificate byte array.
     */
    private fun getSignatureSha256(certificate: ByteArray): String {
        val md: MessageDigest
        try {
            md = MessageDigest.getInstance("SHA256")
        } catch (noSuchAlgorithmException: NoSuchAlgorithmException) {
            Timber.tag(TAG).e("No such algorithm: $noSuchAlgorithmException")
            throw RuntimeException("Could not find SHA256 hash algorithm", noSuchAlgorithmException)
        }
        md.update(certificate)

        // This code takes the byte array generated by `md.digest()` and joins each of the bytes
        // to a string, applying the string format `%02x` on each digit before it's appended, with
        // a colon (':') between each of the items.
        // For example: input=[0,2,4,6,8,10,12], output="00:02:04:06:08:0a:0c"
        return md.digest().joinToString(":") { String.format("%02x", it) }
    }

    private data class KnownCallerInfo(
        internal val name: String,
        internal val packageName: String,
        internal val signatures: MutableSet<KnownSignature>
    )

    private data class KnownSignature(
        internal val signature: String,
        internal val release: Boolean
    )

    /**
     * Convenience class to hold all of the information about an app that's being checked
     * to see if it's a known caller.
     */
    private data class CallerPackageInfo(
        internal val name: String,
        internal val packageName: String,
        internal val uid: Int,
        internal val signature: String?
    )
}

private const val TAG = "PackageValidator"
private const val ANDROID_PLATFORM = "android"
private val WHITESPACE_REGEX = "\\s|\\n".toRegex()