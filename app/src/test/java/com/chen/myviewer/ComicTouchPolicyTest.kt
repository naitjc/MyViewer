package com.chen.myviewer

import com.chen.myviewer.reader.comic.*
import org.junit.Assert.*
import org.junit.Test

class ComicTouchPolicyTest {
    @Test fun leftToRightUsesLeftPreviousRightNextAndMiddleMenu() {
        assertEquals(ComicTapAction.PREVIOUS, ComicTouchPolicy.tap(50f, 1000, false, false))
        assertEquals(ComicTapAction.NEXT, ComicTouchPolicy.tap(950f, 1000, false, false))
        assertEquals(ComicTapAction.MENU, ComicTouchPolicy.tap(500f, 1000, false, false))
    }
    @Test fun rightToLeftReversesOnlyTheEdgeActions() {
        assertEquals(ComicTapAction.NEXT, ComicTouchPolicy.tap(50f, 1000, true, false))
        assertEquals(ComicTapAction.PREVIOUS, ComicTouchPolicy.tap(950f, 1000, true, false))
        assertEquals(ComicTapAction.MENU, ComicTouchPolicy.tap(500f, 1000, true, false))
    }
    @Test fun enlargedImageDoesNotAccidentallyTurnThePage() {
        assertEquals(ComicTapAction.MENU, ComicTouchPolicy.tap(50f, 1000, true, true))
        assertEquals(ComicTapAction.MENU, ComicTouchPolicy.tap(950f, 1000, false, true))
    }
}
