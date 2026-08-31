package com.codeaza.bhaiyaaa.ui.navigation

/**
 * Which of the app's two navigation motions a move should use.
 *
 * Kept as a pure function on route names rather than living inside the
 * NavHost's transition lambdas, because it encodes a design decision that
 * would otherwise be untestable and would quietly rot: adding a screen and
 * forgetting it is a detail rather than a tab makes it slide the wrong way,
 * and nothing would catch that.
 */
object NavMotion {

    /**
     * True when both ends of the move are bottom-bar tabs.
     *
     * Tabs are peers, so moving between them crossfades. Sliding would imply
     * one sits inside the other, and the direction would be a lie - there is
     * no order to Home, Calls and Contacts to move along.
     *
     * Everything else is a drill into or out of a detail screen, where the
     * hierarchy is real and the direction is what tells you which way you
     * moved.
     */
    fun isLateral(from: String?, to: String?): Boolean =
        from in BottomDestination.routes && to in BottomDestination.routes
}
