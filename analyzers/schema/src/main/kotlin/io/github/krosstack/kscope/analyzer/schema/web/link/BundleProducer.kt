package io.github.krosstack.kscope.analyzer.schema.web.link

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef

/** What may have written the files of a copied path: the [target] to link to, and the evidence and certainty of it. */
internal data class BundleProducer(val target: NodeRef, val evidence: EvidenceClass, val certainty: Certainty)
