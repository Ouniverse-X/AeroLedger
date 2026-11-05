package cn.edu.buaa.ledger.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import cn.edu.buaa.ledger.data.DraftStore

@Composable fun rememberFormDraft(key: String): Map<String,String> {
    val context=LocalContext.current
    return remember(key) { DraftStore(context).read(key) }
}

@Composable fun TrackFormDraft(key: String, values: Map<String,String>, original: Map<String,String>, onDirty:(Boolean)->Unit) {
    val context=LocalContext.current
    val store=remember(context) { DraftStore(context) }
    val changed=values!=original
    SideEffect {
        if(changed)store.save(key,values) else store.clear(key)
        onDirty(changed)
    }
}
