# Optional CUDA embeddings

CPU is the default. Build the unpublished GPU payload with
`:components:semantic-worker:cudaDistZip` (output: `build/cuda-distributions`);
it substitutes `onnxruntime_gpu:1.31.1`
and contains natives for Windows x64 and Linux x64 only. It is separate from the
shipped component and its pinned manifest. CUDA and cuDNN are not bundled.

Launch the GPU payload with the model directory followed by `cuda`. The service
selects this argument with `-Dkscope.semantic.device=cuda`; the default is `cpu`.
Use the GPU payload as the component directory supplied to `WorkerEmbeddingProvider`.
Set `quantization` in the checksum-verified `model.json` to `fp16` or `fp32` for
float exports. Supply an NVIDIA driver, CUDA 13 and cuDNN 9 through the process
`PATH` on Windows or `LD_LIBRARY_PATH` on Linux. Initialization failure returns a
failed handshake; it does not retry with CPU.

Float and CUDA identities carry device, precision and a SHA-256 fingerprint in
the opaque revision. The recipe binds the descriptor text (including file hashes),
ONNX Runtime version, thread counts and document/query batch policy. The legacy
int8 CPU identity and vectors remain unchanged. `ModelIdentity` and protocol v1
retain their shapes. Descriptor formatting changes invalidate float reuse.

Run `:components:semantic-worker:cudaIntegrationTest` with system properties
`kscope.semantic.cuda.libraries` (library directories joined with the platform
path separator) and `kscope.semantic.cuda.model` (GPU model directory). For fp16,
also set `kscope.semantic.cuda.cpuModel` to the corresponding fp32 export.
`kscope.semantic.cuda.javaHome` optionally selects the child JVM. Without the
required properties the integration test is skipped.

Set `-Dkscope.semantic.profile=<file-prefix>` on the worker JVM to write ONNX Runtime
profiling events when the session closes. The integration test checks these events
for nodes executed by `CUDAExecutionProvider` and tests CUDA initialization failure
using the GPU runtime with no visible CUDA device.
