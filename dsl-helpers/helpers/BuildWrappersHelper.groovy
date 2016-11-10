package helpers

class BuildWrappersHelper {

    static void addWrappers(def job)  {
        job.with {
            wrappers {
                maskPasswords()
                timestamps()
                colorizeOutput('xterm')
                sshAgent('ee906bef-2d9c-45af-a55a-c357b1b1886a')
                buildUserVars()
                rbenv('2.2.2') {
                    gems('bundler', 'rake')
                    ignoreLocalVersion()
                }
            }
            configure { project ->
                project / buildWrappers / 'io.chef.jenkins.ChefIdentityBuildWrapper' {        
                    jobIdentity('chef12tdev')      
                }
              }
              configure { project ->
                project / buildWrappers / 'EnvInjectPasswordWrapper' {
                    injectGlobalPasswords(true)   
                    maskPasswordParameters(true)   
                }
              }
        }
    }

    static void addEnvironmentVariables(def job)  {
        job.with {
            environmentVariables {
                keepSystemVariables(true)
                keepBuildVariables(true)
                groovy('''
                  import hudson.model.ParametersAction
                  import hudson.model.StringParameterValue

                  def out = [:]

                  def environment = currentBuild.getAction(ParametersAction).getParameters().find { it.name == \'environment\' }.value
                  // get upstream job and copy env vars to current job
                  def upstreamJob = currentBuild.getAction(ParametersAction).getParameters().find { it.name == "upstreamJob${environment}" }
                  [\'cookbookVersion\', \'componentVersion\', \'commit\', \'branch\'].each {
                    out[it] = upstreamJob.getRun().getEnvVars().get(it) 
                  }  

                  // set build name to something like: "#338 origin/master J107"
                  env = currentBuild.getEnvironment()
                  currentBuild.setDisplayName("${env[\'BUILD_DISPLAY_NAME\']} ${out[\'branch\']} ${out[\'componentVersion\']}")

                  env = currentBuild.getEnvironment()
                  if ("true".equals(dry_run)) currentBuild.setDisplayName("${env[\'BUILD_DISPLAY_NAME\']} DRY RUN!")

                  return out
                '''.stripIndent().trim())

              }

        }

    }
    static void addEnvironmentVariablesStep(def job, String service, String environment){
        job.with {
            steps {
                environmentVariables {
                    env('EXECUTED_BY_USER', '${BUILD_USER_ID}')
                    env('service', service)
                    env('role', "${service}-${environment}")
                    env('versions', "{\"apps\":{\"${service}\":\"\$componentVersion\"},\"cookbooks\":{\"${service}\":\"\$cookbookVersion\"}}")
                  }
            }
        }
    }
    static void addInstallWMDorchStep(def job){
        job.with {
            steps {
                shell('''
                    rm -rf Gemfile.lock

                    cat <<EOM > Gemfile
                    source "https://rubygems.org"
                    gem "wmdorch", :git => "https://$GITHUB_URL/plan/wmdorch"
                    EOM

                    gem uninstall wmdorch
                    bundle install
                '''.stripIndent().trim())
            }
        }
    }
    
    static void addGenerateWMDorchConfigFileStep(def job, String chefEnv, String environment){
        job.with {
            steps {
                shell("""
                    mkdir -p ci
                    cat <<EOM > ci/wmdorch.yml
                    ---
                      wmdorch: 
                        segments: 
                          - 
                            name: \${service}-${environment}                         
                            chef_env: ${chefEnv}
                            chef_roles: []
                            apps:
                              -
                                name: \${service}
                                chef_role: \${role}
                    EOM
                """.stripIndent().trim())
            }
        }
    }

    static void addExecWMDorchStepDev(def job, String environment){
        job.with {
            steps {
                shell("""
                    bundle exec wmdorch \\
                      --manual_deploy true \\
                      --task deploy \\
                      --versions \$versions \\
                      --wm_env \${service}-${environment} \\
                      --config ci/wmdorch.yml \\
                      --log_level debug \\
                      --ssh_key_path \"\$JENKINS_HOME/credenciatls/jenkins.key\" \\
                      --git_working_dir \"\$WORKSPACE\" \\
                      --knife_config_path \"\$WORKSPACE/.chef/knife.rb\" \\
                      --dry_run \${dry_run}
                """.stripIndent().trim())
            }
        }
    }

    static void addExecWMDorchStepProd(def job){
        job.with {
            steps {
                shell('''
                    chmod 0600 $CIBUILD_PROD_RSA $GIT_USER_RSA
                    bundle exec wmdorch \\
                      --manual_deploy true \\
                      --task deploy \\
                      --versions $versions \\
                      --wm_env ${service}-production \\
                      --config ci/wmdorch.yml \\
                      --log_level info \\
                      --ssh_key_path $CIBUILD_PROD_RSA $GIT_USER_RSA \\
                      --git_working_dir "$WORKSPACE" \\
                      --knife_config_path "$WORKSPACE/.chef/knife.rb" \\
                      --dry_run ${dry_run}
                '''.stripIndent().trim())
            }
        }
    }
    static void addFeedToggleStep(def job, String feedtoggle){
        job.with {
            steps {
                shell("""
                    sleep 60
                    ${feedtoggle}
                """.stripIndent().trim())
              }

        }
    }

    static void addParameters(def job, String service){
        job.with {
            parameters {
                choiceParam('environment', ['Fusion', 'QA'],'')
                runParam('upstreamJobQA', "${service}-qa", '', 'SUCCESSFUL')
                runParam('upstreamJobFusion', "${service}-fusion", '', 'SUCCESSFUL')
                booleanParam('dry_run', false, 'check for dry run')
                booleanParam('FORCE_DEPLOY', false, '')
              }

        }
    }

    static void addTestServiceStep(def job, String host ){
        job.with {
            steps {
                shell("""
                    #!/bin/bash

                    url=\"${host}/\${service}/_platform/status\"
                    path=\'.resource.health.summary.status\'
                    count=10
                    status=\"\"
                    delay=3
                    time=\$(( \$delay * \$count ))

                    echo \"Checking Status of Service: \$url\"
                    until [ \"\$status\" = \"OK\" ] || [ \$count -eq 0 ]; do
                      status=`curl -s \$url | jq \$path | tr -d \'\"\'` 
                      echo \"status=\$status\"
                      count=\$((count - 1))
                      if [ \"\$status\" != \"OK\" ]; then
                          echo \"waiting \$delay seconds to retry\"
                          sleep \$delay
                      fi
                    done

                    if [ \"\$status\" = \"OK\" ]; then
                      echo \"service is up and running an status is OK!\"
                    elif [ \"\$status\" = \"WARNING\" ] || [ \"\$status\" = \"CRITICAL\" ]; then
                      echo \"WARNING: service is up and running BUT status is \$status\"
                    else
                      echo \"service failed to start after \$time seconds. status=\$status\"
                      exit 1
                    fi

                """.stripIndent().trim())
              }

        }
    }

    static void addDelGapChefStep(def job){
        job.with {
            steps {
                shell("""
                    rm -rf /var/lib/jenkins/jobs/\${role}/workspace/gapChef
                """.stripIndent().trim())
              }

        }
    }

    static void addFeedToggle2Step(def job, String feedtoggle){
        job.with {
            steps {
                shell("""
                    #!/bin/bash

                    TRIES=\"4\"
                    SERVICE=\"${feedtoggle}\"

                    echo \"Setting Toogle Feature to the Service: \$SERVICE\"
                    echo \"Please wait.....\"
                    until [ \"\$Try\" == \"\$TRIES\" ] || [ \$httpStatusValue ]
                    do
                            sleep 15
                            httpStatusValue=`curl -f -s -H \'Content-Type: application/json\' -X POST -d \'{ \"isActive\" : true }\' \${SERVICE} | jq \'.httpStatus\'`
                            ((Try += 1))
                    done

                    if [ \"\$Try\" -ge \"\$TRIES\" ];then
                        echo \"ERROR: Time Out setting Toogle Configuration\"
                        exit 200
                    fi

                    if [ \"\$httpStatusValue\" != \"200\" ];then
                            echo \"ERROR: The Toogle Feature could not be set HTTP Error: \$httpStatusValue\"
                            exit 100
                    fi
                    
                    echo \"Toogle Feature Set....................[ OK ]\"
                    
                """.stripIndent().trim())
              }

        }
    }

    static void addPostBuildGroovy(def job){
        job.with {
            publishers {
                groovyPostBuild {
                 behavior(Behavior.DoNothing)
                  sandbox(false)
                  script('''
                      def env = manager.build.getEnvironment(manager.listener)
                    summary = manager.createSummary("warning.gif")
                    summary.appendText("<ul><li><h3>Cookbook Version: ${env[\'cookbookVersion\']}</h3></li>", false)
                    summary.appendText("<li><h3>Commit: <a href=http://github.gapinc.dev/plan/${env[\'service\']}/commit/${env[\'commit\']}>${env[\'commit\']}</h3></a></li></ul>",false)
                      '''.stripIndent().trim())
        }
      }

        }
    }

}
