package com.example.roadbuddy

import android.Manifest
import android.content.Context
import android.os.*
import android.util.Log
import android.widget.Switch
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.roadbuddy.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var vibrator: Vibrator

    private val viewModel: MainViewModel by viewModels()

    private var count = 0
    private var avgHR = 0.00
    private var driving = false
    private var currentHR : Double = 0.0
    private var fallingAsleep = false

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
                when (result) {
                    true -> {
                        Log.i(TAG, "Body sensors permission granted")
                        lifecycleScope.launchWhenStarted {
                            viewModel.measureHeartRate()
                        }
                    }
                    false -> Log.i(TAG, "Body sensors permission not granted")
                }
            }

        lifecycleScope.launchWhenStarted {
            viewModel.uiState.collect {
                updateViewVisiblity(it)
            }
        }
        lifecycleScope.launchWhenStarted {
            viewModel.heartRateAvailable.collect {
                binding.statusText.text = getString(R.string.measure_status, it)
            }
        }
        lifecycleScope.launchWhenStarted {
            viewModel.heartRateBpm.collect {
                count++
                avgHR = ((avgHR*count-1) + currentHR) / count
                if (driving) {
                    updateViewVisiblity(UiState.Driving)
                    binding.lastMeasuredValue.text = String.format("%.1f", it);
                    currentHR = it
                    if (currentHR < 62) {
                        binding.sleepAwake.text = "Falling Asleep!"
                        fallingAsleep = true
                        vibrateRepeatedly()
                    }
                    else {
                        binding.sleepAwake.text = "Awake"
                        fallingAsleep = false
                        stopVibrate()
                    }
                }
                else {
                    binding.lastMeasuredValue.text = "0.0"
                    updateViewVisiblity(UiState.HeartRateAvailable)
                }
            }
        }

        val switchOn = findViewById<Switch>(R.id.driving_switch)
        switchOn?.setOnCheckedChangeListener { _, isChecked ->
            driving = !driving
        }
    }

    override fun onStart() {
        super.onStart()
        permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
    }

    private fun vibrateRepeatedly(){
        val vibrationEffect = VibrationEffect.createWaveform(
            longArrayOf(0, 500, 400, 200),
            intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE),
            2
        )
        vibrator.vibrate(vibrationEffect)
    }

    private fun stopVibrate(){
        vibrator.cancel()
    }

    private fun updateViewVisiblity(uiState: UiState) {
        (uiState is UiState.Startup).let {
            binding.progress.isVisible = it
        }
        (uiState is UiState.HeartRateNotAvailable).let {
            binding.brokenHeart.isVisible = it
            binding.notAvailable.isVisible = it
        }
        (uiState is UiState.HeartRateAvailable).let {
            binding.lastMeasuredLabel.isVisible = it
            binding.lastMeasuredValue.isVisible = it
            binding.heart.isVisible = it
        }
        (uiState is UiState.Driving).let {
            binding.lastMeasuredLabel.isVisible = it
            binding.lastMeasuredValue.isVisible = it
            binding.heart.isVisible = it
            binding.sleepAwake.isVisible = it
        }
    }

}
