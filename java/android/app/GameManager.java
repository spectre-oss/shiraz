// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import java.util.Objects;

public final class GameManager {
    public static final int GAME_MODE_UNSUPPORTED = 0;
    public static final int GAME_MODE_STANDARD = 1;
    public static final int GAME_MODE_PERFORMANCE = 2;
    public static final int GAME_MODE_BATTERY = 3;
    public static final int GAME_MODE_CUSTOM = 4;

    private volatile GameState mLastState;

    GameManager() {
    }

    public int getGameMode() {
        return GAME_MODE_STANDARD;
    }

    public void setGameState(GameState gameState) {
        mLastState = Objects.requireNonNull(gameState, "gameState");
    }

    GameState shirazLastGameState() {
        return mLastState;
    }
}
