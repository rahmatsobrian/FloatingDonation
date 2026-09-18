package siroha.floating.donation

import android.app.Application
import siroha.floating.donation.util.Logger

class SirohaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.i("Siroha Floating Donation initialized")
    }
}
