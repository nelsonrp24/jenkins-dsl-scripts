package helpers

class TrainingJobHelper {

    static void addAllTrainingJobHelpers(def job,String service,String environment,String platform,String service_cookbook,String chefEnv)  {
        helpers.BuildWrappersHelper.addWrappers(job)
        helpers.BuildWrappersHelper.addEnvironmentVariables(job)
        helpers.BuildWrappersHelper.addEnvironmentVariablesStep(job,service,environment)
        helpers.BuildWrappersHelper.addInstallWMDorchStep(job)
        helpers.BuildWrappersHelper.addGenerateWMDorchConfigFileStep(job,chefEnv,environment)
        helpers.BuildWrappersHelper.addExecWMDorchStepDev(job,environment)
        helpers.BuildWrappersHelper.addParameters(job,service)
        helpers.BuildWrappersHelper.addPostBuildGroovy(job)
    }

}
