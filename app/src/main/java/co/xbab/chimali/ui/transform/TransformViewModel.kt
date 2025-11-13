package co.xbab.chimali.ui.transform

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import co.xbab.chimali.R

class TransformViewModel : ViewModel() {

    private val _texts = MutableLiveData<List<TransformItem>>().apply {
        value = (1..16).mapIndexed { index, i ->
            TransformItem(
                text = "This is item # $i",
                icon = when (index) {
                    0 -> R.drawable.avatar_1
                    1 -> R.drawable.avatar_2
                    2 -> R.drawable.avatar_3
                    3 -> R.drawable.avatar_4
                    4 -> R.drawable.avatar_5
                    5 -> R.drawable.avatar_6
                    6 -> R.drawable.avatar_7
                    7 -> R.drawable.avatar_8
                    8 -> R.drawable.avatar_9
                    9 -> R.drawable.avatar_10
                    10 -> R.drawable.avatar_11
                    11 -> R.drawable.avatar_12
                    12 -> R.drawable.avatar_13
                    13 -> R.drawable.avatar_14
                    14 -> R.drawable.avatar_15
                    else -> R.drawable.avatar_16
                }
            )
        }
    }

    val texts: LiveData<List<TransformItem>> = _texts
}