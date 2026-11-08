package com.websnake.recentstv

import android.view.KeyEvent
import com.websnake.recentstv.logic.Btn

/** Remote / D-pad key codes → the logic's buttons. Anything else (volume, power…)
 *  is OTHER and passes through to the system. */
object Keys {
    fun btn(code: Int): Btn = when (code) {
        KeyEvent.KEYCODE_DPAD_LEFT -> Btn.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Btn.RIGHT
        KeyEvent.KEYCODE_DPAD_UP -> Btn.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Btn.DOWN
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A -> Btn.OK
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B -> Btn.BACK
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> Btn.MENU
        else -> Btn.OTHER
    }
}
