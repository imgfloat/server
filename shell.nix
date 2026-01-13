{ pkgs ? import <nixpkgs> { } }:

pkgs.mkShell {
  packages = [
    pkgs.jdt-language-server
    pkgs.libxkbcommon
    pkgs.maven
    pkgs.openjdk
  ];
}
