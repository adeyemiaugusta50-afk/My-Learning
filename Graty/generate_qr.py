import argparse
import ipaddress
from pathlib import Path
from urllib.parse import urlsplit

import qrcode


def validate_checkin_url(url: str) -> None:
    parsed = urlsplit(url)
    hostname = parsed.hostname
    if not hostname:
        raise ValueError("The check-in URL must include a hostname or IP address.")
    if hostname == "localhost" or hostname.endswith(".localhost"):
        raise ValueError("Do not make an office QR code for a localhost URL.")
    try:
        address = ipaddress.ip_address(hostname)
    except ValueError:
        if parsed.scheme != "https":
            raise ValueError(
                "Use HTTPS for a public hostname; HTTP is allowed only for a private LAN IP."
            )
        return
    if address.is_loopback or address.is_link_local or address.is_unspecified:
        raise ValueError("Do not make an office QR code for a local-only address.")
    if parsed.scheme == "https" and not address.is_global:
        raise ValueError("A public HTTPS QR URL must use a public IP address.")
    if parsed.scheme == "http" and not address.is_private:
        raise ValueError(
            "Use HTTP only for a private LAN IP; public URLs must use HTTPS."
        )
    if parsed.scheme not in ("http", "https"):
        raise ValueError("The check-in URL must use HTTP or HTTPS.")


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Create a printable QR code for a check-in page. "
            "Use the office's private LAN IP for HTTP or a public host's HTTPS URL."
        )
    )
    parser.add_argument("url", help="The check-in page URL visitors should open")
    parser.add_argument(
        "-o",
        "--output",
        default="office-checkin-qr.png",
        help="Output PNG file (default: office-checkin-qr.png)",
    )
    args = parser.parse_args()
    validate_checkin_url(args.url)
    image = qrcode.make(args.url)
    output_path = Path(args.output)
    image.save(output_path)
    print(f"QR code saved to {output_path.resolve()}")


if __name__ == "__main__":
    main()
