package ng.kbmovies.customercare

import android.app.Application
import ng.kbmovies.customercare.data.CareRepository

class CareApp : Application() {
    val repository by lazy { CareRepository(this) }
}
