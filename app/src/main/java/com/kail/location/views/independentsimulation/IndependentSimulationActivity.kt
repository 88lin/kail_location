package com.kail.location.views.independentsimulation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.kail.location.R
import com.kail.location.views.base.BaseActivity
import com.kail.location.views.settings.SettingsActivity
import com.kail.location.views.theme.locationTheme
import com.kail.location.viewmodels.IndependentSimulationViewModel

class IndependentSimulationActivity : BaseActivity() {
    private val viewModel: IndependentSimulationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val version = try {
            "v${packageManager.getPackageInfo(packageName, 0).versionName}"
        } catch (_: Exception) { "" }

        val onNavigate: (Int) -> Unit = { id ->
            when (id) {
                R.id.nav_location_simulation -> {
                    startActivity(Intent(this, com.kail.location.views.locationsimulation.LocationSimulationActivity::class.java))
                    finish()
                }
                R.id.nav_route_simulation -> {
                    startActivity(Intent(this, com.kail.location.views.routesimulation.RouteSimulationActivity::class.java))
                    finish()
                }
                R.id.nav_navigation_simulation -> {
                    startActivity(Intent(this, com.kail.location.views.navigationsimulation.NavigationSimulationActivity::class.java))
                    finish()
                }
                R.id.nav_independent_simulation -> { }
                R.id.nav_root_app_hide -> {
                    startActivity(Intent(this, com.kail.location.views.roothide.RootAppHideActivity::class.java))
                    finish()
                }
                R.id.nav_wifi_simulation -> {
                    startActivity(Intent(this, com.kail.location.views.wifisimulation.WifiSimulationActivity::class.java))
                    finish()
                }
                R.id.nav_cell_simulation -> {
                    startActivity(Intent(this, com.kail.location.views.cellsimulation.CellSimulationActivity::class.java))
                    finish()
                }
                R.id.nav_camera_simulation -> {
                    startActivity(Intent(this, com.kail.location.views.camerasimulation.CameraSimulationActivity::class.java))
                    finish()
                }
                R.id.nav_nfc_simulation -> {
                    startActivity(Intent(this, com.kail.location.views.nfcsimulation.NfcSimulationActivity::class.java))
                    finish()
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                else -> {}
            }
        }

        setContent {
            locationTheme {
                IndependentSimulationScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                    onNavigate = onNavigate,
                    appVersion = version
                )
            }
        }
    }
}
