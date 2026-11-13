package com.beatlane.app

import android.car.Car
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beatlane.app.ui.drive.DriveScreen
import com.beatlane.app.ui.drive.DriveViewModel
import com.beatlane.app.ui.drive.Stage
import com.beatlane.app.ui.predrive.PreDriveScreen
import com.beatlane.app.ui.report.ReportScreen
import com.beatlane.app.ui.theme.BeatLaneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeatLaneTheme {
                val vm: DriveViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()

                // 차량 속도(CAR_SPEED)는 런타임 권한. 처음 한 번 요청하고, 결과로 속도 구독을 다시 시도한다.
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                    vm::onSpeedPermissionResult,
                )
                val requestSpeedPermission = {
                    if (checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Car.PERMISSION_SPEED)
                    }
                }
                LaunchedEffect(Unit) { requestSpeedPermission() }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.recheckVehicle() }

                when (state.stage) {
                    Stage.PRE_DRIVE -> PreDriveScreen(
                        state = state,
                        onPassenger = vm::setPassenger,
                        onPowertrain = vm::setPowertrain,
                        onSource = vm::selectSource,
                        onRequestSpeedPermission = requestSpeedPermission,
                        onSound = vm::setSoundOn,
                        onStart = vm::startDrive,
                    )
                    Stage.DRIVING -> {
                        // 뒤로 가기 = 주행 종료 → 리포트
                        BackHandler(onBack = vm::finishDrive)
                        DriveScreen(
                            state = state,
                            onPlay = vm::play,
                            onPowertrain = vm::setPowertrain,
                            onPassenger = vm::setPassenger,
                            onFinish = vm::finishDrive,
                            onSound = vm::setSoundOn,
                        )
                    }
                    Stage.REPORT -> {
                        val report = state.report
                        if (report != null) {
                            BackHandler(onBack = vm::newDrive)
                            ReportScreen(report, onNewDrive = vm::newDrive)
                        }
                    }
                }
            }
        }
    }
}
