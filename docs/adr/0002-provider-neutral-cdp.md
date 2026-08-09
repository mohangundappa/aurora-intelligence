# ADR 0002: Provider-neutral CDP adapter

Customer profiles and consent are accessed through `CdpAdapter`. The local simulator implements that boundary and is explicitly an adjacent stand-in, not a replacement for a CDP platform. Provider adapters can be added without changing signal or decision consumers.
