package SessionManagement;

public enum DealingSessionState {
    WaitForJoiningACK,
    WaitForIDACK,
    WaitForSessionObjectACK,
    WaitForCommitmentACK,
    WaitForShareACK,
    WaitForIntermediateCalculationACK,
    WaitForIntermediateDistributionACK,
    WaitForDistributionACK,
    WaitForReconstructionACK
}
