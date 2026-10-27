<#
.SYNOPSIS
    Test harness for coverage policy, schema, floor, inventory, and lifecycle fixtures.
.DESCRIPTION
    Runs deterministic JSON fixtures under tools/coverage/fixtures/ using PowerShell 7
    and built-in .NET XML/JSON/BigInteger types without external dependencies.
#>
[CmdletBinding()]
param(
    [string]$FixturePattern = "*.json",
    [string]$SpecificCase = $null
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$CommonPath = Join-Path $ScriptDir "coverage-common.ps1"

if (-not (Test-Path $CommonPath)) {
    Write-Error "Shared utility script not found: $CommonPath"
    exit 3
}

. $CommonPath

$FixturesDir = Join-Path $ScriptDir "fixtures"
$fixtureFiles = Get-ChildItem -Path $FixturesDir -Filter $FixturePattern -File | Where-Object { $_.Name -ne "package.json" -and $_.Name -ne "schema-fixture.json" }

if ($fixtureFiles.Count -eq 0) {
    Write-Error "No fixture files found matching '$FixturePattern' in $FixturesDir"
    exit 1
}

$totalPassed = 0
$totalFailed = 0
$results = @()

foreach ($file in $fixtureFiles) {
    Write-Host "Running fixtures in $($file.Name)..." -ForegroundColor Cyan
    $content = Get-Content $file.FullName -Raw | ConvertFrom-Json

    if (-not $content.cases) {
        Write-Host "  No cases defined in $($file.Name)" -ForegroundColor Yellow
        continue
    }

    foreach ($case in $content.cases) {
        if ($SpecificCase -and $case.id -ne $SpecificCase) {
            continue
        }

        $casePassed = $false
        $errorMsg = $null

        try {
            switch -Wildcard ($file.Name) {
                "schema.json" {
                    $validation = Test-ManifestSchema -Manifest $case.manifest
                    if ($case.expected_valid) {
                        if ($validation.IsValid) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Expected valid, but got errors: $($validation.Errors -join '; ')"
                        }
                    } else {
                        if (-not $validation.IsValid) {
                            if ($case.expected_error) {
                                $matched = $validation.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) {
                                    $casePassed = $true
                                } else {
                                    $errorMsg = "Expected error matching '$($case.expected_error)', but got: $($validation.Errors -join '; ')"
                                }
                            } else {
                                $casePassed = $true
                            }
                        } else {
                            $errorMsg = "Expected invalid manifest, but validation succeeded"
                        }
                    }
                }
                "floors.json" {
                    $eval = Test-CoverageFloors -Counters $case.counters -Policy $case.policy
                    if ($eval.Passed -eq $case.expected_pass) {
                        $casePassed = $true
                    } else {
                        $errorMsg = "Expected pass=$($case.expected_pass), got pass=$($eval.Passed). Reasons: $($eval.Reasons -join '; ')"
                    }
                }
                "inventory.json" {
                    $invEval = Test-InventoryAccounting -Inventory $case.inventory -XmlData $case.xml_file -CanonicalProof $case.proof -Case $case
                    if ($case.expected_valid) {
                        if ($invEval.IsValid) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Expected valid, got errors: $($invEval.Reasons -join '; ')"
                        }
                    } else {
                        if (-not $invEval.IsValid) {
                            if ($case.expected_error) {
                                $matched = $invEval.Reasons | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) {
                                    $casePassed = $true
                                } else {
                                    $errorMsg = "Expected error matching '$($case.expected_error)', got: $($invEval.Reasons -join '; ')"
                                }
                            } else {
                                $casePassed = $true
                            }
                        } else {
                            $errorMsg = "Expected invalid inventory, but validation succeeded"
                        }
                    }
                }
                "policy.json" {
                    $polEval = Test-PolicyConfiguration -Policy $case.policy -Arguments $case.arguments
                    if ($case.expected_valid) {
                        if ($polEval.IsValid) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Expected valid, got errors: $($polEval.Reasons -join '; ')"
                        }
                    } else {
                        if (-not $polEval.IsValid) {
                            if ($case.expected_error) {
                                $matched = $polEval.Reasons | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) {
                                    $casePassed = $true
                                } else {
                                    $errorMsg = "Expected error matching '$($case.expected_error)', got: $($polEval.Reasons -join '; ')"
                                }
                            } else {
                                $casePassed = $true
                            }
                        } else {
                            $errorMsg = "Expected invalid policy, but validation succeeded"
                        }
                    }
                }
                "freshness.json" {
                    $freshEval = Test-CapturePreflight -Preflight $case.preflight
                    if ($case.expected_valid) {
                        if ($freshEval.IsValid) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Expected valid preflight, got errors: $($freshEval.Errors -join '; ')"
                        }
                    } else {
                        if (-not $freshEval.IsValid) {
                            if ($case.expected_error) {
                                $matched = $freshEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) {
                                    $casePassed = $true
                                } else {
                                    $errorMsg = "Expected error matching '$($case.expected_error)', got: $($freshEval.Errors -join '; ')"
                                }
                            } else {
                                $casePassed = $true
                            }
                        } else {
                            $errorMsg = "Expected invalid preflight, but validation succeeded"
                        }
                    }
                }
                "suites.json" {
                    $suiteEval = Test-SuiteOutcome -SuiteResult $case.suite_result
                    $statusOk = ($suiteEval.Status -eq $case.expected_status)
                    $execOk = ($suiteEval.Executed -eq $case.expected_executed)
                    if ($statusOk -and $execOk) {
                        if ($case.expected_error) {
                            $matched = $suiteEval.Reasons | Where-Object { $_ -like "*$($case.expected_error)*" }
                            if ($matched) {
                                $casePassed = $true
                            } else {
                                $errorMsg = "Expected error matching '$($case.expected_error)', got: $($suiteEval.Reasons -join '; ')"
                            }
                        } else {
                            $casePassed = $true
                        }
                    } else {
                        $errorMsg = "Expected status=$($case.expected_status), executed=$($case.expected_executed); got status=$($suiteEval.Status), executed=$($suiteEval.Executed)"
                    }
                }
                "collection.json" {
                    $collEval = Test-CollectionState -Run $case.collection_run
                    $statusOk = ($collEval.CollectionStatus -eq $case.expected_collection_status)
                    $eligOk = ($collEval.BaselineEligible -eq $case.expected_baseline_eligible)
                    if ($statusOk -and $eligOk) {
                        if ($case.expected_error) {
                            $matched = $collEval.Reasons | Where-Object { $_ -like "*$($case.expected_error)*" }
                            if ($matched) {
                                $casePassed = $true
                            } else {
                                $errorMsg = "Expected error matching '$($case.expected_error)', got: $($collEval.Reasons -join '; ')"
                            }
                        } else {
                            $casePassed = $true
                        }
                    } else {
                        $errorMsg = "Expected status=$($case.expected_collection_status), eligible=$($case.expected_baseline_eligible); got status=$($collEval.CollectionStatus), eligible=$($collEval.BaselineEligible)"
                    }
                }
                "integration.json" {
                    $intEval = Test-IntegrationInvocation -Invocation $case.invocation
                    if ($case.PSObject.Properties['expected_valid']) {
                        if ($case.expected_valid) {
                            if ($intEval.IsValid) { $casePassed = $true }
                            else { $errorMsg = "Expected valid, got errors: $($intEval.Errors -join '; ')" }
                        } else {
                            if (-not $intEval.IsValid) {
                                if ($case.expected_error) {
                                    $matched = $intEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                                    if ($matched) { $casePassed = $true }
                                    else { $errorMsg = "Expected error matching '$($case.expected_error)', got: $($intEval.Errors -join '; ')" }
                                } else { $casePassed = $true }
                            } else { $errorMsg = "Expected invalid invocation, but validation succeeded" }
                        }
                    } elseif ($case.PSObject.Properties['expected_integration_status']) {
                        if ($intEval.IntegrationStatus -eq $case.expected_integration_status) { $casePassed = $true }
                        else { $errorMsg = "Expected integration status $($case.expected_integration_status), got $($intEval.IntegrationStatus)" }
                    }
                }
                "capture-result.json" {
                    try {
                        $captureResult = ConvertFrom-CaptureResultOutput -OutputLines $case.output_lines
                        if ($case.expected_valid) {
                            if ($captureResult.run_id -eq $case.expected_run_id -and
                                $captureResult.run_dir -eq $case.expected_run_dir) {
                                $casePassed = $true
                            } else {
                                $errorMsg = "Capture result identity mismatch: run_id=$($captureResult.run_id), run_dir=$($captureResult.run_dir)"
                            }
                        } else {
                            $errorMsg = "Expected capture result parsing to fail, but it succeeded"
                        }
                    } catch {
                        if (-not $case.expected_valid -and $_.Exception.Message.Contains([string]$case.expected_error)) {
                            $casePassed = $true
                        } else {
                            $errorMsg = $_.Exception.Message
                        }
                    }
                }
                "manifest-identity.json" {
                    $identity = Test-CandidateManifestIdentity -Manifest $case.manifest -ExpectedSourceCommit $case.expected_source_commit -ExpectedTargetCommit $case.expected_target_commit
                    if ($identity.IsValid -eq $case.expected_valid) {
                        if (-not $case.expected_error -or ($identity.Errors -join '; ').Contains([string]$case.expected_error)) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Expected identity error '$($case.expected_error)', got: $($identity.Errors -join '; ')"
                        }
                    } else {
                        $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($identity.IsValid). Errors: $($identity.Errors -join '; ')"
                    }
                }
                "comparison.json" {
                    $compEval = Test-ComparisonCase -Case $case
                    if ($case.PSObject.Properties['expected_passed']) {
                        if ($compEval.Passed -eq $case.expected_passed) { $casePassed = $true }
                        else { $errorMsg = "Expected passed=$($case.expected_passed), got $($compEval.Passed). Errors: $($compEval.Errors -join '; ')" }
                    } elseif ($case.PSObject.Properties['expected_baseline_valid']) {
                        if ($compEval.BaselineValid -eq $case.expected_baseline_valid) { $casePassed = $true }
                        else { $errorMsg = "Expected baseline valid=$($case.expected_baseline_valid), got $($compEval.BaselineValid)" }
                    } elseif ($case.PSObject.Properties['expected_compatible']) {
                        if ($compEval.Compatible -eq $case.expected_compatible) { $casePassed = $true }
                        else { $errorMsg = "Expected compatible=$($case.expected_compatible), got $($compEval.Compatible)" }
                    }
                }
                "publication.json" {
                    $pubEval = Test-PublicationCase -Case $case
                    if ($case.expected_valid) {
                        if ($pubEval.IsValid) { $casePassed = $true }
                        else { $errorMsg = "Expected valid publication, got errors: $($pubEval.Errors -join '; ')" }
                    } else {
                        if (-not $pubEval.IsValid) {
                            if ($case.expected_error) {
                                $matched = $pubEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) { $casePassed = $true }
                                else { $errorMsg = "Expected error matching '$($case.expected_error)', got: $($pubEval.Errors -join '; ')" }
                            } else { $casePassed = $true }
                        } else { $errorMsg = "Expected invalid publication, but validation succeeded" }
                    }
                }
                "selection.json" {
                    $selEval = Test-SelectionCase -Case $case
                    if ($case.expected_error) {
                        $matched = $selEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                        if ($matched) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Expected error matching '$($case.expected_error)', got: $($selEval.Errors -join '; ')"
                        }
                    } else {
                        if ($selEval.IsValid) {
                            $casePassed = $true
                        } else {
                            $errorMsg = "Selection case mismatch: $($selEval.Errors -join '; ')"
                        }
                    }
                }
                "baselines.json" {
                    $baseEval = Test-BaselineCase -Case $case
                    if ($baseEval.Reusable -eq $case.expected_reusable) {
                        if ($case.expected_error) {
                            $matched = $baseEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                            if ($matched) { $casePassed = $true }
                            else { $errorMsg = "Expected error matching '$($case.expected_error)', got: $($baseEval.Errors -join '; ')" }
                        } else {
                            $casePassed = $true
                        }
                    } else {
                        $errorMsg = "Expected reusable=$($case.expected_reusable), got $($baseEval.Reusable)"
                    }
                }
                "trusted-comparison.json" {
                    $comparison = Test-TrustedComparisonManifest -CandidateManifest $case.candidate -BaselineManifest $case.baseline
                    if ($comparison.IsValid -eq $case.expected_valid) {
                        if (-not $case.expected_error -or ($comparison.Errors -join '; ').Contains([string]$case.expected_error)) { $casePassed = $true }
                        else { $errorMsg = "Expected error '$($case.expected_error)', got: $($comparison.Errors -join '; ')" }
                    } else {
                        $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($comparison.IsValid). Errors: $($comparison.Errors -join '; ')"
                    }
                }
                "accepted-baseline-index.json" {
                    $indexEval = Test-AcceptedBaselineIndex -Index $case.index -ExpectedTargetCommit $case.expected_target -ActualBundleDigest $case.actual_digest
                    if ($indexEval.IsValid -eq $case.expected_valid) {
                        if (-not $case.expected_error -or ($indexEval.Errors -join '; ').Contains([string]$case.expected_error)) { $casePassed = $true }
                        else { $errorMsg = "Expected index error '$($case.expected_error)', got: $($indexEval.Errors -join '; ')" }
                    } else {
                        $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($indexEval.IsValid). Errors: $($indexEval.Errors -join '; ')"
                    }
                }
                "changed-paths.json" {
                    $coveragePolicy = Read-SafeJson -Path (Join-Path $ScriptDir 'policy.json')
                    $pathsProvided = [bool]$case.paths_provided
                    [string[]]$changed = @()
                    if ($case.paths) { $changed = @($case.paths) }
                    $affectedModules = @(Get-AffectedTier1Modules -Policy $coveragePolicy -ChangedPaths $changed -ChangedPathsProvided $pathsProvided)
                    $expectedModules = @($case.expected_modules)
                    if (($affectedModules -join ',') -ceq ($expectedModules -join ',')) { $casePassed = $true }
                    else { $errorMsg = "Expected modules [$($expectedModules -join ',')], got [$($affectedModules -join ',')]" }
                }
                "promotion-identity.json" {
                    $promotion = Test-PromotionIdentity -Manifest $case.manifest -IntegratedTree $case.integrated_tree -IntegratedCommit $case.integrated_commit
                    if ($promotion.IsValid -eq $case.expected_valid) {
                        if (-not $case.expected_error -or ($promotion.Errors -join '; ').Contains([string]$case.expected_error)) { $casePassed = $true }
                        else { $errorMsg = "Expected promotion error '$($case.expected_error)', got: $($promotion.Errors -join '; ')" }
                    } else {
                        $errorMsg = "Expected valid=$($case.expected_valid), got valid=$($promotion.IsValid). Errors: $($promotion.Errors -join '; ')"
                    }
                }
                "archive-lifecycle.json" {
                    $archEval = Test-ArchiveLifecycleCase -Case $case
                    if ($case.PSObject.Properties['expected_success']) {
                        if ($archEval.Success -eq $case.expected_success) {
                            if ($case.expected_error) {
                                $matched = $archEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) { $casePassed = $true }
                                else { $errorMsg = "Expected error matching '$($case.expected_error)', got: $($archEval.Errors -join '; ')" }
                            } else { $casePassed = $true }
                        } else {
                            $errorMsg = "Expected success=$($case.expected_success), got $($archEval.Success)"
                        }
                    } elseif ($case.PSObject.Properties['expected_promotion_allowed']) {
                        if ($archEval.PromotionAllowed -eq $case.expected_promotion_allowed) {
                            if ($case.expected_error) {
                                $matched = $archEval.Errors | Where-Object { $_ -like "*$($case.expected_error)*" }
                                if ($matched) { $casePassed = $true }
                                else { $errorMsg = "Expected error matching '$($case.expected_error)', got: $($archEval.Errors -join '; ')" }
                            } else { $casePassed = $true }
                        } else {
                            $errorMsg = "Expected promotion_allowed=$($case.expected_promotion_allowed), got $($archEval.PromotionAllowed)"
                        }
                    }
                }
                default {
                    Write-Host "  [SKIP] $($case.id): No handler registered for $($file.Name)" -ForegroundColor DarkGray
                    continue
                }
            }
        } catch {
            $errorMsg = "Exception during test execution: $_"
        }

        if ($casePassed) {
            $totalPassed++
            Write-Host "  [PASS] $($case.id) - $($case.description)" -ForegroundColor Green
            $results += [PSCustomObject]@{
                ID = $case.id
                Status = "PASS"
                Description = $case.description
                Notes = ""
            }
        } else {
            $totalFailed++
            Write-Host "  [FAIL] $($case.id) - $($case.description)" -ForegroundColor Red
            Write-Host "         Reason: $errorMsg" -ForegroundColor DarkRed
            $results += [PSCustomObject]@{
                ID = $case.id
                Status = "FAIL"
                Description = $case.description
                Notes = $errorMsg
            }
        }
    }
}

Write-Host "`nTest Summary: $totalPassed passed, $totalFailed failed" -ForegroundColor $(if ($totalFailed -eq 0 -and $totalPassed -gt 0) { "Green" } else { "Red" })

if (($totalPassed + $totalFailed) -eq 0) {
    Write-Error "Zero test cases were selected or executed (SpecificCase='$SpecificCase', Pattern='$FixturePattern')"
    exit 1
}

if ($totalFailed -gt 0) {
    exit 1
}
exit 0
