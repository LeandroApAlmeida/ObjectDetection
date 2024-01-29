package tensorflow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import org.tensorflow.lite.examples.objectdetection.R

class PermissionsFragment : Fragment() {

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts
    .RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(
                context,
                "Permissão concedida",
                Toast.LENGTH_LONG
            ).show()
            navigateToCamera()
        } else {
            Toast.makeText(
                context,
                "Permissão negada",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) -> {
                navigateToCamera()
            }
            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.CAMERA
                )
            }
        }
    }

    private fun navigateToCamera() {
        lifecycleScope.launchWhenStarted {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                PermissionsFragmentDirections.actionPermissionsToCamera()
            )
        }
    }

    companion object {
        fun hasPermissions(context: Context) = arrayOf(Manifest.permission.CAMERA).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

}
