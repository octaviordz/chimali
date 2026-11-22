# Candidate physical API 35 heap lifecycle cohort — 2026-11-22

**Candidate:** commit `2627644114f95f7f0c33e7448abc5e0f3961cc26`, tree `82661bc30660c8ca77a69207e2c246f83caf3109`.

**Environment:** Physical ASUS AI2302, serial `R7AIB7002901AZ3`, Android 15 / API 35. All captures used synthetic markers and the isolated `heapAudit` target/test package pair. The target and test APK SHA-256 values matched the previously frozen candidate build: `6DF7A9D64F19563726D7783DEA675C1F2234E492C5870B8A3D63A0816DFCDB21` and `DA90871B833A2AC08CDC9064BDC95B4EF7F0B22DEA81E40D7F11F1E96C913B46`.

## Runs and observations

Each Android instrumentation run passed one lifecycle test. Shark 2.14 inspected exact synthetic marker matches, object fields/arrays, and GC-root paths. The independent positive-control `String` and backing `byte[]` had two rooted matches at every checkpoint in all four runs, demonstrating that the analysis could find a known live owner.

| Owner group | Test | Checkpoints | Synthetic matches (hits / rooted) | Terminal finding |
|---|---|---|---|---|
| Wallet recovery/derivation | `activityDisposalClearsSyntheticMnemonicOwnerWithoutRetainingLifecycleObjects` | baseline, derivation, active, disposed, terminal | baseline 0/0; derivation 9/0; active 14/3; disposed 2/0; terminal 0/0 | Active mnemonic owner traced to `DevToolsViewModel.state`; active derivation bytes traced to `WalletMasterSeedProvider.cachedPrk` and `cachedParent`/`HdkKeyPair.privateKey`. No synthetic marker had a terminal root path. |
| Vault note | `vaultDetailDisposalClearsSyntheticNoteAndStoredItemOwners` | baseline, active, disposed, terminal | baseline 0/0; active 42/7; disposed 4/0; terminal 4/0 | Active note fields traced through `VaultViewModel.state` and `selectedSecureNotePayload`. No synthetic marker had a terminal root path. |
| Vault password/card | `vaultPasswordAndCardDisposalClearsSyntheticPayloadOwners` | baseline, active, disposed, terminal | baseline 0/0; active 189/21; disposed 42/0; terminal 42/0 | Active payload fields traced through `VaultViewModel.state` and selected password/card payloads. No synthetic marker had a terminal root path. |
| Passkey identity | `credentialManagementDisposalClearsSyntheticPasskeyIdentityOwners` | baseline, active, terminal | baseline 0/0; active 51/10; terminal 12/0 | Active identity fields traced through `CredentialManagementViewModel.fullCredentialList` and `PasskeyCredential`. No synthetic marker had a terminal root path. |

The remaining unrooted marker matches in disposed/terminal captures are reported as heap matches with no GC-root path; their physical origin is unknown. No forced garbage collection was used, and absence of a root path does not prove memory overwrite, native/provider erasure, or physical erasure. Each test's owned-array assertions passed. Raw HPROFs and expected-marker dictionaries remain restricted; their exact filenames and SHA-256 digests are listed below and in the controlled capture summaries.

## Restricted capture artifacts

The following files are retained in `C:\Users\octav\AppData\Local\Chimali\SEC065-heap`. The marker dictionaries and HPROFs contain synthetic values only and remain under the access/retention controls in [heap-artifact-access.md](heap-artifact-access.md).

| Run | Restricted artifact manifest |
|---|---|
| Wallet recovery/derivation, `sec065heapcandidateapi35wallet20261122` | See run summary SHA-256 `19244A7BF2857C3E26E1445E8F4F4159A083EBBA773D4F6A9ADB3E39A7AE6D05`; expected-marker dictionary `CCFF0F38B7D3B53D2B3EE22A8FBF15674CB0F14E799A50BC27A53ED811954FAC`; instrumentation `1B408D8CC413F76592E336391290C04B65D90E0F6FA3FC90473A4ED89F3D9046`; HPROFs: baseline `7BA7B424EDAD236B92E91DDF9DAFFB6234D920E7D12A1011811C78BBCBE029F4`, derivation `78C8B04B60F3A0B1E640812FC9C88E61DAB7D43A5442A7F1CEEA692BA34C5BE0`, active `CBF802C2E8C6BC6A277DFAC3D43202C78EB0915AA0284D960898906CCA0487E6`, disposed `1C6E7AC65C33948FE37532650B962B6FAFB4403D2C768D1F2E44A717D5341A66`, terminal `13A3CB42513220E39690FEC18A7A8DA9ACA5F62490337F6B6B235DDFBC9C203A`. |
| Vault note, `sec065heapcandidateapi35vaultnote20261122` | Run summary `BF81090EF7C809C3B62496B24F7AF00364D751A8D97DBE28BDDA3C0D10820181`; expected-marker dictionary `F2D4A8326DD38F539478E423EBD3CB2E30B79B7FA20BAF9D43B9316CE61F598F`; instrumentation `5BAEC3EAE18E01F20DBF9E2A81691719070BD89445D28FC7BD1407E7C6912429`; HPROFs: baseline `600683C53F3C6FF8A04D012F9DE4D873C68CE667FD15FA41931EFBE92DC0F84D`, active `5AC2A0A1BC1C4FBDC510B8BAC9337C3C852911D3FA98AFFB46371FF689E2A353`, disposed `E935731105C93FD51B37335B61CA35E0481760EE2FC1FF7A67A2A112576837B5`, terminal `E33E0BD3BDAF4518E59C3A3C4A45E9F1EA1A5046E3A94A405525D3F823E3CB47`. |
| Vault password/card, `sec065heapcandidateapi35vaultpayload20261122` | Run summary `9B3B68E98810C95B3A134825B8B5CB31033599006CD9CB8FB4E3A0D2B4950E3E`; expected-marker dictionary `F3524F443B203841F9EA8AB5F58CEAEC483A2EAB5C5B73A14B052A7E49F34A84`; instrumentation `D2D2ED43F5F5B28EA89566F0BEB623CDE274D013B2C53FE3F0FA715475F9943F`; HPROFs: baseline `A326806C888EF0E31E7912F8D37BDE81C6B0B736974539E88EB1535A9A75FE46`, active `670116415E48FFFEE9CE0197DE010F27CDDF3F6049B537A18CDC85662A58C3DB`, disposed `39764145D59D21265F1D5B91796CB862606CB96EA1DD7A2CE291AB3E63659443`, terminal `F0F5168FB28E2F2BE11445456CD13CD7896BF51CC022E7120E2C18377C70DEF2`. |
| Passkey identity, `sec065heapcandidateapi35passkey20261122` | Run summary `2E482FA89B82BC08B8531558B3BE9046C3D82960CF069135C77CC3CA23498436`; expected-marker dictionary `1E270E9FD8B40CC2944C29339A584F91B3062C8A1F8214468753047370F1DB0B`; instrumentation `9141781C427187C234816A8EBDC25C9AECB5A519672D181B36C1F7E51503210D`; HPROFs: baseline `8E46C0CE348FA4336C0D4B2B0D53321BFE32DEF0F2F9EC46D99778E887E0E82C`, active `E9A975C81D29F707E842E1B9D1DE858C531B4D612AA3431FC049C27055D5E328`, terminal `DDCAED6B245A52F25DF159CB0AEBD6093D28BB30CC018171376463777587DF2D`. |

## Sanitized analysis files

The committed `heap-candidate-physical-api35-20261122/` directory contains the exact sanitized Shark output used for the counts and owner paths above. The output contains marker labels and object/root metadata, not marker values.

| Analysis file | SHA-256 |
|---|---|
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35passkey20261122_active_analysis.txt` | `DF107F901FB0456CD24DB2FE7D513E0FB21F6D6824EB118C546180E9FC5D5A15` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35passkey20261122_baseline_analysis.txt` | `EFD5D659A8104B015F8B576679403ECC79B30653F49D0195A80A9B7751033608` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35passkey20261122_terminal_analysis.txt` | `B05F8BC7C0DD1334A703F67E25132CE0EAA58AAB36BB769E7A7062563E162DCD` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35vaultnote20261122_active_analysis.txt` | `BC9ED38189A34E7818D5D6D17BE212A2EC994AAA8DECBF3C7045C242548BA0E4` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35vaultnote20261122_baseline_analysis.txt` | `1A9C2069398515DB390434BEF69747E2103CAE2F7CA0436E66A2573FC94E32B2` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35vaultnote20261122_disposed_analysis.txt` | `45B5BA3824A9D2B30647A6924A08C36B10C670194797D676274FC77CED4C2304` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35vaultnote20261122_terminal_analysis.txt` | `8E38B4A4EFB94E9D78418531FCB2A4430D6130B34DF29DE58BAA71C501F50119` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35vaultpayload20261122_active_analysis.txt` | `B4A6C850260FC854DF402AC9605894E43E46979792947260E37C6ACCD303C8D4` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35vaultpayload20261122_baseline_analysis.txt` | `F79B290F71CC2A669585FF92E6FBA26F0DBED4EE71C0EB3EA29A645C3F3468A9` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35vaultpayload20261122_disposed_analysis.txt` | `412CEAE77A919E2CCA96576D1F93F682A1FC6738B0EEB77DADBB9B61609B400F` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35vaultpayload20261122_terminal_analysis.txt` | `157D7BE6C8D909046DBD4E9F01C37C338A2834DCD90E2B0036F9B24CD31FC1CF` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35wallet20261122_active_analysis.txt` | `9728A3868CF23D67AE6CFA1AF4316514CFDDC4DB2F25BF9D0E985C1F8D6222A4` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35wallet20261122_baseline_analysis.txt` | `7617D3F92DD58D6B88C3D704DD1082DFC2F4E4F2085E7574D62B8C8F2ACAC7B1` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35wallet20261122_derivation_analysis.txt` | `8D379238B2755DDA951ADE703F80B446DB95822937D4543A6131965B9236ED22` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35wallet20261122_disposed_analysis.txt` | `73442BEC6AFEFDCD5C5E19489880132C9F0A9DC76CAAA62B9C8D2750F41A402C` |
| `heap-candidate-physical-api35-20261122/sec065heapcandidateapi35wallet20261122_terminal_analysis.txt` | `B913D2995E8108549AE817C6BA4F62AB24DEBAA02F99B2F91ECF8A28586FE799` |

