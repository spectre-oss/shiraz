{
  description = "Shiraz build tools for the Ubuntu AppImage and browser assembler pipeline";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/b6018f87da91d19d0ab4cf979885689b469cdd41";

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forSystems = f: nixpkgs.lib.genAttrs systems (system: f system);
    in {
      devShells = forSystems (system:
        let pkgs = import nixpkgs { inherit system; };
        in {
          default = pkgs.mkShellNoCC {
            packages = with pkgs; [
              autoconf automake bison cmake curl file flex gettext git
              go gnumake gzip jdk17_headless libtool meson ninja nodejs_22
              patchelf python3 ripgrep unzip xz zip zstd
            ];
          };
        });
    };
}
