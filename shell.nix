{ pkgs ? import <nixpkgs> { } }:

pkgs.mkShell {
  packages = [
    pkgs.electron
    pkgs.jdt-language-server
    pkgs.libxkbcommon
    pkgs.maven
    pkgs.mesa
    pkgs.nodePackages.prettier
    pkgs.nodejs
    pkgs.nss
    pkgs.openbox
    pkgs.openjdk
    pkgs.vulkan-loader
    pkgs.wayland
    pkgs.wayland-protocols
    pkgs.weston
    pkgs.xorg.xorgserver
    pkgs.xwayland
  ];
}
