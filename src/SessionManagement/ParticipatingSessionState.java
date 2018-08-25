package SessionManagement;

public enum ParticipatingSessionState {
    WaitForNewSession,
    WaitForID,
    WaitForSessionObject,
    WaitForShare,
    WaitForIntermediateCalculation,
    WaitForIntermediateDistribution,
    WaitForReconstruction,
    WaitForOthersShare,
    WaitForIntermediateOthersShare,
    Finished
}
