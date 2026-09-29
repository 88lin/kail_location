package com.kail.location.views.xposedsettings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kail.location.R
import com.kail.location.views.base.BaseActivity
import com.kail.location.viewmodels.LocationSimulationViewModel
import com.kail.location.views.theme.locationTheme
import com.kail.location.views.routesimulation.RouteSimulationActivity
import com.kail.location.views.locationsimulation.LocationSimulationActivity
import com.kail.location.views.navigationsimulation.NavigationSimulationActivity
import com.kail.location.views.settings.SettingsActivity
import com.kail.location.utils.GoUtils

class XposedSettingsActivity : BaseActivity() {

    private val viewModel: LocationSimulationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }

        setContent {
            locationTheme {
                val runMode by viewModel.runMode.collectAsState()

                XposedSettingsScreen(
                    onNavigate = { id ->
                        when (id) {
                            R.id.nav_location_simulation -> {
                                startActivity(Intent(this, LocationSimulationActivity::class.java))
                                finish()
                            }
                            R.id.nav_route_simulation -> {
                                startActivity(Intent(this, RouteSimulationActivity::class.java))
                                finish()
                            }
                            R.id.nav_navigation_simulation -> {
                                startActivity(Intent(this, NavigationSimulationActivity::class.java))
                                finish()
                            }
                            R.id.nav_nfc_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.nfcsimulation.NfcSimulationActivity::class.java))
                                finish()
                            }
                            R.id.nav_independent_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.independentsimulation.IndependentSimulationActivity::class.java))
                                finish()
                            }
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
                            R.id.nav_sandbox -> {
                                startActivity(Intent(this, com.kail.location.views.sandbox.SandboxActivity::class.java))
                                finish()
                            }
                            R.id.nav_settings -> {
                                startActivity(Intent(this, SettingsActivity::class.java))
                                finish()
                            }
                            else -> {
                                Toast.makeText(this, getString(R.string.error_under_development), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    appVersion = version,
                    runMode = runMode,
                    onRunModeChange = { viewModel.setRunMode(it) },
                    onDeveloperModeSelected = {
                        if (GoUtils.isAllowMockLocation(this)) {
                            viewModel.setRunMode("developer")
                        } else {
                            GoUtils.openMockLocationSettings(this)
                        }
                    },
                    onXposedSettingsSelected = {
                        // Already here
                    }
                )
            }
        }
    }
}
