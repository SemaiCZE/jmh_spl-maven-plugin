package cz.cuni.mff.d3s.spl;


import java.lang.annotation.*;

@Inherited
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SPLFormula {
	String value();
}
