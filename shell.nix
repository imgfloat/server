{ pkgs ? import <nixpkgs> { } }:

pkgs.mkShell {
  packages = [
    pkgs.openssl
    pkgs.openjdk
    pkgs.maven
    pkgs.nodejs
    pkgs.nodePackages.prettier
    pkgs.jdt-language-server
    pkgs.mkcert
    pkgs.nss
  ];
}
