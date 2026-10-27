// SPDX-License-Identifier: GPL-3.0-only
#include <SDL.h>
#include <cstdio>
#include <initializer_list>

int main() {
    if (SDL_Init(SDL_INIT_VIDEO)) {
        std::fprintf(stderr, "SDL video: %s\n", SDL_GetError());
        return 1;
    }
    for (const auto mode : {SDL_RENDERER_SOFTWARE, SDL_RENDERER_ACCELERATED}) {
        SDL_Window* window = SDL_CreateWindow("Shiraz Wayland smoke",
            SDL_WINDOWPOS_UNDEFINED, SDL_WINDOWPOS_UNDEFINED, 320, 480,
            SDL_WINDOW_RESIZABLE | SDL_WINDOW_ALLOW_HIGHDPI);
        if (!window) {
            std::fprintf(stderr, "SDL window: %s\n", SDL_GetError());
            return 2;
        }
        SDL_Renderer* renderer = SDL_CreateRenderer(window, -1, mode);
        if (!renderer) {
            std::fprintf(stderr, "SDL renderer (%u): %s\n", mode, SDL_GetError());
            return 3;
        }
        SDL_SetRenderDrawColor(renderer, 10, 20, 30, 255);
        if (SDL_RenderClear(renderer)) {
            std::fprintf(stderr, "SDL clear: %s\n", SDL_GetError());
            return 4;
        }
        SDL_RenderPresent(renderer);
        SDL_DestroyRenderer(renderer);
        SDL_DestroyWindow(window);
    }
    SDL_Quit();
    return 0;
}
