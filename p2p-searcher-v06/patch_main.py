from pathlib import Path

p = Path('p2p-build/app/src/main/java/app/p2psearchernext/android/MainActivity.java')
s = p.read_text(encoding='utf-8')
needle = '''                    tasks.add(new Callable<SourceBatch>() {
                        @Override public SourceBatch call() {
                            LegacyEd2kSearch.SearchBatch result = LegacyEd2kSearch.search(query);
                            return new SourceBatch(result.items, result.errors);
                        }
                    });
'''
kad = '''                    tasks.add(new Callable<SourceBatch>() {
                        @Override public SourceBatch call() {
                            NativeKadSearch.SearchBatch result = NativeKadSearch.search(query);
                            return new SourceBatch(result.items, result.errors);
                        }
                    });
'''
if needle not in s:
    raise SystemExit('Legacy eD2K task insertion anchor missing')
p.write_text(s.replace(needle, needle + kad, 1), encoding='utf-8')
