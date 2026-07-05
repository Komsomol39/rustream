package com.komsomol.rustream.di

import android.content.Context
import com.komsomol.rustream.data.search.KinozalCookieStore
import com.komsomol.rustream.data.search.KinozalProvider
import com.komsomol.rustream.data.search.NnmClubProvider
import com.komsomol.rustream.data.search.NnmCookieStore
import com.komsomol.rustream.data.search.RuTorProvider
import com.komsomol.rustream.data.search.RuTrackerCookieStore
import com.komsomol.rustream.data.search.RuTrackerProvider
import com.komsomol.rustream.data.search.SearchRepository
import com.komsomol.rustream.data.search.YtsProvider
import com.komsomol.rustream.data.settings.SettingsRepository
import com.komsomol.rustream.data.torrent.DownloadRepository
import com.komsomol.rustream.data.torrent.TorrentEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideSettingsRepository(@ApplicationContext ctx: Context) = SettingsRepository(ctx)

    @Provides @Singleton
    fun provideRuTrackerCookieStore(@ApplicationContext ctx: Context) = RuTrackerCookieStore(ctx)

    @Provides @Singleton
    fun provideNnmCookieStore(@ApplicationContext ctx: Context) = NnmCookieStore(ctx)

    @Provides @Singleton
    fun provideRuTrackerProvider(cookies: RuTrackerCookieStore) = RuTrackerProvider(cookies)

    @Provides @Singleton
    fun provideNnmClubProvider(cookies: NnmCookieStore) = NnmClubProvider(cookies)

    @Provides @Singleton
    fun provideRuTorProvider(settings: SettingsRepository) = RuTorProvider(settings)

    @Provides @Singleton
    fun provideKinozalCookieStore(@ApplicationContext ctx: Context) = KinozalCookieStore(ctx)

    @Provides @Singleton
    fun provideKinozalProvider(cookies: KinozalCookieStore) = KinozalProvider(cookies)

    @Provides @Singleton
    fun provideYtsProvider() = YtsProvider()

    @Provides @Singleton
    fun provideSearchRepository(
        ruTor: RuTorProvider,
        ruTracker: RuTrackerProvider,
        kinozal: KinozalProvider,
        nnm: NnmClubProvider,
        yts: YtsProvider,
        settings: SettingsRepository
    ) = SearchRepository(ruTor, ruTracker, kinozal, nnm, yts, settings)

    @Provides @Singleton
    fun provideTorrentEngine(@ApplicationContext ctx: Context) = TorrentEngine(ctx)

    @Provides @Singleton
    fun provideDownloadRepository(
        engine: TorrentEngine,
        rtCookies: RuTrackerCookieStore,
        nnmCookies: NnmCookieStore,
        kinozalCookies: KinozalCookieStore
    ) = DownloadRepository(engine, rtCookies, nnmCookies, kinozalCookies)
}
