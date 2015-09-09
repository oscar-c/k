// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.krun.modes;

import com.google.inject.Inject;
import org.kframework.Rewriter;
import org.kframework.attributes.Source;
import org.kframework.builtin.BooleanUtils;
import org.kframework.definition.Rule;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kore.K;
import org.kframework.kore.KORE;
import org.kframework.kore.KVariable;
import org.kframework.krun.KRun;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.SearchResult;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import scala.Tuple2;

import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Execution Mode for Conventional KRun
 */
public class KRunExecutionMode implements ExecutionMode {

    private final KRunOptions kRunOptions;
    private final KExceptionManager kem;
    private final FileUtil files;

    @Inject
    public KRunExecutionMode(KRunOptions kRunOptions, KExceptionManager kem, FileUtil files) {
        this.kRunOptions = kRunOptions;
        this.kem = kem;
        this.files = files;
    }


    @Override
    public Object execute(K k, Rewriter rewriter, CompiledDefinition compiledDefinition) {
        Rule parsedPattern;
        Rule pattern;
        if (kRunOptions.pattern != null) {
            parsedPattern = KRun.parsePattern(files, kem, kRunOptions.pattern, compiledDefinition, Source.apply("<command line>"));
            pattern = KRun.compilePattern(files, kem, kRunOptions.pattern, kRunOptions, compiledDefinition, Source.apply("<command line>"));
        } else {
            pattern = new Rule(KORE.KVariable("X"), BooleanUtils.TRUE, BooleanUtils.TRUE, compiledDefinition.executionModule().att());
            parsedPattern = pattern;

        }
        if (kRunOptions.search()) {
            return new SearchResult(rewriter.search(k, Optional.ofNullable(kRunOptions.depth), Optional.ofNullable(kRunOptions.bound), pattern), parsedPattern);
        }
        if (kRunOptions.exitCodePattern != null) {
            pattern = KRun.compilePattern(files, kem, kRunOptions.exitCodePattern, kRunOptions, compiledDefinition, Source.apply("<command line: --exit-code>"));
            Tuple2<K, List<? extends Map<? extends KVariable, ? extends K>>> res = rewriter.executeAndMatch(k, Optional.ofNullable(kRunOptions.depth), pattern);
            return Tuple2.apply(res._1(), KRun.getExitCode(kem, res._2()));
        }
        return rewriter.executeAndMatch(k, Optional.ofNullable(kRunOptions.depth), pattern)._1();
    }
}
