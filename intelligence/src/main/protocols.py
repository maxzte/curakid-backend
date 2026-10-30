from typing import Protocol, Dict, Any, List

class ClinicalTriageProtocol(Protocol):
    def decide_clinical_triage(self, state: Dict[str, Any]) -> Dict[str, Any]:
        """Performs clinical triage decisions over the state."""
        ...

class ChunkRerankerProtocol(Protocol):
    def rerank_and_filter_chunks(
        self,
        query: str,
        child_context: Dict[str, Any],
        chunks: List[Dict[str, Any]]
    ) -> List[Dict[str, Any]]:
        """Evaluates, reranks and filters candidate medical chunks."""
        ...

class ReportGuardrailProtocol(Protocol):
    def evaluate_medical_report(
        self,
        child_context: Dict[str, Any],
        chunks: List[Dict[str, Any]],
        report_text: str,
        recommendations: str
    ) -> Dict[str, Any]:
        """Evaluates generated report for medical faithfulness, relevance and safety."""
        ...

class ChatTriageProtocol(Protocol):
    def triage_chat_message(
        self,
        message: str,
        child_context: Dict[str, Any]
    ) -> Dict[str, Any]:
        """Classifies incoming parent chat message for urgency, intent and RAG retrieval."""
        ...

class SystemOneDecisionProtocol(
    ClinicalTriageProtocol,
    ChunkRerankerProtocol,
    ReportGuardrailProtocol,
    ChatTriageProtocol,
    Protocol
):
    """Unified System 1 decision interface."""
    @property
    def is_ready(self) -> bool:
        ...
