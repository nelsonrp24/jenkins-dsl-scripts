def job = job('testing1') {
}
helpers.TrainingJobHelper.addAllTrainingJobHelpers(job,'serviceName','envDevelop', 'linux','','testing')
helpers.BuildWrappersHelper.addTestServiceStep(job,'testing1.maranda.com:8080') 
