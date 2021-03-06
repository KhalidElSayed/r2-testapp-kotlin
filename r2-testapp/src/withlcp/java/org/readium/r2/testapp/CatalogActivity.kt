/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp

import android.app.ProgressDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import com.mcxiaoke.koi.HASH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.Appcompat
import org.jetbrains.anko.design.longSnackbar
import org.jetbrains.anko.design.textInputLayout
import org.readium.r2.lcp.LcpHttpService
import org.readium.r2.lcp.LcpLicense
import org.readium.r2.lcp.LcpSession
import org.readium.r2.shared.Publication
import org.readium.r2.shared.drm.DRMModel
import org.readium.r2.shared.drm.Drm
import org.readium.r2.streamer.parser.EpubParser
import org.readium.r2.streamer.parser.PubBox
import timber.log.Timber
import java.io.File
import java.net.URL
import kotlin.coroutines.CoroutineContext

class CatalogActivity : LibraryActivity(), LcpFunctions, CoroutineScope {
    /**
     * Context of this scope.
     */
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listener = this
    }

//    Import a DRM license ( needs to be online )
//
//    An app which imports a DRM license will follow these steps (see the previous section for more details):
//
//    1/ Validate the license structure and check its profile identifier
//
//    2/ Get the passphrase associated with the license
//
//    3/ Validate the license integrity
//
//    4/ Check the license status
//
//    5/ Get an updated license if needed
//
//    6/ Fetch the encrypted publication
//
//    7/ Register the device / license
//
//    8/ Open the publication


//    Open a protected publication stored in the app catalog ( can work offline as well )
//
//    The process is a simpler than when the protected publication is imported, as some information about the license is stored in the database, especially the license identifier.
//
//    4/ Check the license status
//
//    5/ Get an updated license if needed
//
//    8/ Open the publication


    override fun parseIntentLcpl(uriString: String, networkAvailable: Boolean) {
        val uri: Uri? = Uri.parse(uriString)
        if (uri != null) {
            try {
                val progress = indeterminateProgressDialog(getString(R.string.progress_wait_while_downloading_book))
                progress.show()
                Thread {
                    try {
                        val bytes = URL(uri.toString()).openStream().readBytes()
                        val lcpLicense = LcpLicense(bytes, this)
                        lcpLicense.evaluate(bytes)?.let { path ->
                            val file = File(path)
                            launch {
                                val parser = EpubParser()
                                val pub = parser.parse(path)
                                if (pub != null) {
                                    val pair = parser.parseEncryption(pub.container, pub.publication, pub.container.drm)
                                    pub.container = pair.first
                                    pub.publication = pair.second
                                    prepareToServe(pub, file.name, file.absolutePath, true, true)
                                    progress.dismiss()
                                    handleLcpPassphrase(file.absolutePath, Drm(Drm.Brand.Lcp), networkAvailable, {
                                        // Do nothing
                                    }, {
                                        // Do nothing
                                    }, {
                                        // Do nothing
                                    })
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.localizedMessage?.let {
                            catalogView.longSnackbar(it)
                        } ?: run {
                            catalogView.longSnackbar("An error occurred")
                        }
                        progress.dismiss()
                    }
                }.start()
            } catch (e: Exception) {
            }
        }
    }

    override fun prepareAndStartActivityWithLCP(drm: Drm, pub: PubBox, book: Book, file: File, publicationPath: String, parser: EpubParser, publication: Publication, networkAvailable: Boolean) {
        if (drm.brand == Drm.Brand.Lcp) {
            prepareToServe(pub, book.fileName, file.absolutePath, false, true)

            handleLcpPassphrase(publicationPath, drm, networkAvailable, { drm1 ->
                val pair = parser.parseEncryption(pub.container, publication, drm1)
                pub.container = pair.first
                pub.publication = pair.second
            }, { drm2 ->
                if (supportedProfiles.contains(drm2.profile)) {
                    server.addEpub(publication, pub.container, "/" + book.fileName, applicationContext.getExternalFilesDir(null).path + "/styles/UserProperties.json")
                    prepareSyntheticPageList(publication, book)

                    val license = drm.license as LcpLicense
                    val drmModel = DRMModel(drm.brand.value,
                            license.archivePath!!)

                    startActivity(intentFor<R2EpubActivity>("publicationPath" to publicationPath, "epubName" to book.fileName, "publication" to publication, "bookId" to book.id, "drmModel" to drmModel))
                } else {
                    alert(Appcompat, "The profile of this DRM is not supported.") {
                        negativeButton("Ok") { }
                    }.show()
                }
            }, {
                // Do nothing
            })


        }
    }

    override fun processLcpActivityResult(uri: Uri, it: Uri, progress: ProgressDialog, networkAvailable: Boolean) {
        try {
            val bytes = contentResolver.openInputStream(uri).readBytes()
            val lcpLicense = LcpLicense(bytes, this@CatalogActivity)
            lcpLicense.evaluate(bytes)?.let { path ->
                val file = File(path)
                launch {
                    val parser = EpubParser()
                    val pub = parser.parse(path)
                    if (pub != null) {
                        val pair = parser.parseEncryption(pub.container, pub.publication, pub.container.drm)
                        pub.container = pair.first
                        pub.publication = pair.second
                        prepareToServe(pub, file.name, file.absolutePath, true, true)
                        progress.dismiss()
                        handleLcpPassphrase(file.absolutePath, Drm(Drm.Brand.Lcp), networkAvailable, {
                            // Do nothing
                        }, {
                            // Do nothing
                        }, {
                            // Do nothing
                        })
                    }
                }

            }
        } catch (e: Exception) {
            e.localizedMessage?.let {
                catalogView.longSnackbar(it)
            } ?: run {
                catalogView.longSnackbar("An error occurred")
            }
            progress.dismiss()
        }

    }

    private fun handleLcpPassphrase(publicationPath: String, drm: Drm, networkAvailable: Boolean, parsingCallback: (drm: Drm) -> Unit, callback: (drm: Drm) -> Unit, callbackUI: () -> Unit) {
        val lcpHttpService = LcpHttpService()
        val session = LcpSession(publicationPath, this)

        fun validatePassphrase(passphraseHash: String):Any? {
            Timber.i("LCP validatePassphrase")
            val preferences = getSharedPreferences("org.readium.r2.lcp", Context.MODE_PRIVATE)

            if (networkAvailable) {

                try {
                    Timber.i("LCP lcpHttpService.certificateRevocationList")
                    val pemCrtl = lcpHttpService.certificateRevocationList("http://crl.edrlab.telesec.de/rl/EDRLab_CA.crl", session)

                    Timber.i("LCP lcpHttpService.certificateRevocationList  %s", pemCrtl)
                    return pemCrtl?.let {
                        preferences.edit().putString("pemCrtl", pemCrtl).apply()
                        val status = session.resolve(passphraseHash, pemCrtl, networkAvailable)
                        if (status is String) {
                            launch {
                                toast("This license was $status")
                            }
                        } else {
                            return@let status
                        }
                    } ?: run {
                        val status = session.resolve(passphraseHash, preferences.getString("pemCrtl", ""), networkAvailable)
                        if (status is String) {
                            launch {
                                toast("This license was $status")
                            }
                        } else {
                            status
                        }
                    }
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    exception.localizedMessage?.let { message ->
                        launch {
                            catalogView.longSnackbar(message)
                        }
                    } ?: run {
                        launch {
                            catalogView.longSnackbar("An error occurred")
                        }
                    }

                }
            } else {
                val status = session.resolve(passphraseHash, preferences.getString("pemCrtl", ""), networkAvailable)
                if (status is String) {
                    launch {
                        toast("This license was $status")
                    }
                } else {
                    return status
                }
            }
            return null
        }

        fun promptPassphrase(reason: String? = null, callback: (pass: String) -> Unit) {
            launch {
                var editTextTitle: EditText? = null

                alert(Appcompat, "Hint: " + session.getHint(), reason ?: "LCP Passphrase") {
                    customView {
                        verticalLayout {
                            textInputLayout {
                                padding = dip(10)
                                editTextTitle = editText {
                                    hint = "Passphrase"
                                }
                            }
                        }
                    }
                    positiveButton("OK") { }
                    negativeButton("Cancel") { }
                }.build().apply {
                    setCancelable(false)
                    setCanceledOnTouchOutside(false)
                    setOnShowListener {
                        val b = getButton(AlertDialog.BUTTON_POSITIVE)
                        b.setOnClickListener { _ ->
                            val passphraseHash = HASH.sha256(editTextTitle!!.text.toString())
                            session.checkPassphrases(listOf(passphraseHash))?.let {pass ->
                                session.storePassphrase(pass)
                                callback(pass)
                                dismiss()
                            } ?:run {
                                launch {
                                    editTextTitle!!.error = "You entered a wrong passphrase."
                                    editTextTitle!!.requestFocus()
                                }
                            }
                        }
                    }

                }.show()
            }
        }

        val passphrases = session.passphraseFromDb()
        passphrases?.let { passphraseHash ->
            val lcpLicense = validatePassphrase(passphraseHash)
            drm.license = lcpLicense as? LcpLicense
            drm.profile = session.getProfile()
            parsingCallback(drm)
            callback(drm)
        } ?: run {
            promptPassphrase(null) { passphraseHash ->
                val lcpLicense = validatePassphrase(passphraseHash)
                drm.license = lcpLicense as? LcpLicense
                drm.profile = session.getProfile()
                parsingCallback(drm)
                callback(drm)
                callbackUI()
            }
        }
    }

}