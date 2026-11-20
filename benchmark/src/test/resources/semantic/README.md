# File ranking replays

Two list-only DEV cases from the public repository `square/moshi` (Apache-2.0).
Parent: `be6cd87645dcb76715aa9644f50dfe1445aadec6`.
Child: `7d51df53f5fcef5946dce46d0bcd9295f37a3b3b`.

`dev-fusion-v1.jsonl` contains `dev-replay-001` (original) and `dev-replay-002`
(paraphrase). Each case contains 160 structural paths, 174 semantic paths and
10 expected fused paths. Both complete input lists were exported before the old
top-10 cut. Expected results retain the original saved order.

Fusion deduplicates each complete list, adds equal reciprocal ranks with offset
60, sorts by descending total and ascending path, and takes ten at the end.

File size: 47,782 bytes. SHA-256:
`02b4f2ba630828b9a0e612400e5cf3de5eaec3cce1553ac25739f70f507f1ec8`.
