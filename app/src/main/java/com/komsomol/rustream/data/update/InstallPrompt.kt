package com.komsomol.rustream.data.update

import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Мост между фоновым InstallReceiver и активным экраном.
 *
 * PackageInstaller присылает подтверждение установки в broadcast-приёмник,
 * но запуск системного окна из фона блокируется прошивками вроде MIUI/HyperOS.
 * Поэтому ресивер кладёт intent сюда, а MainActivity (на переднем плане)
 * запускает его сам — так окно установки надёжно появляется на любом устройстве.
 */
object InstallPrompt {
    val confirmIntent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
}
