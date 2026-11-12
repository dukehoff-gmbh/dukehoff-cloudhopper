import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import CodeBlock from '@theme/CodeBlock';
import Heading from '@theme/Heading';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import HopSvg from '@site/static/img/hop.svg';

import styles from './index.module.css';

const VERSION = '1.1.0';

const EXAMPLE = `public class HelloFunction {

    @Function(name = "hello")
    @HttpTrigger(method = "GET", path = "/hello/{name}")
    public String hello(@PathParam String name) {
        return "hello " + name;
    }
}`;

const TARGETS = [
  {profile: 'aws-terraform', name: 'AWS Lambda', note: 'API Gateway, EventBridge, SQS, S3'},
  {profile: 'azure-terraform', name: 'Azure Functions', note: 'HTTP, timer, queue and blob triggers'},
  {profile: 'gcp-terraform', name: 'Google Cloud Functions', note: 'API Gateway, Cloud Scheduler, Pub/Sub, Cloud Storage'},
  {profile: 'springboot-http', name: 'Spring Boot', note: 'Your own servers'},
];

const STEPS = [
  {
    title: 'Annotate a plain method',
    text: (
      <>
        <code>@Function</code> plus a trigger. No cloud SDK, no handler
        interface, no vendor types in your code.
      </>
    ),
  },
  {
    title: 'Pick the target in the build',
    text: (
      <>
        An annotation processor generates handler classes, Terraform and the API
        description for the profile you build: <code>mvn install -Paws-terraform</code>.
      </>
    ),
  },
  {
    title: 'Deploy, compare, move',
    text: (
      <>
        <code>terraform apply</code> the generated configuration. To change
        providers, build with another profile. The code stays the same.
      </>
    ),
  },
];

const ARCHETYPE = `mvn archetype:generate \\
  -DarchetypeGroupId=eu.cloudhopper.mc \\
  -DarchetypeArtifactId=cloudhopper-maven-archetype-core \\
  -DarchetypeVersion=${VERSION} \\
  -DgroupId=com.example -DartifactId=my-function \\
  -DgeneratorIds=aws-terraform,gcp-terraform,azure-terraform,springboot-http`;

function Hero() {
  return (
    <header className={styles.hero}>
      <div className={clsx('container', styles.heroGrid)}>
        <div className={styles.heroText}>
          <p className={styles.eyebrow}>
            <span className={styles.dot} aria-hidden="true" />
            Version {VERSION} on Maven Central
          </p>
          <Heading as="h1" className={styles.heroTitle}>
            Write once, run anywhere. <span className={styles.accent}>Revisited.</span>
          </Heading>
          <p className={styles.heroLead}>
            Serverless Java that doesn't know which cloud it runs on. Too
            expensive? Data has to stay in the EU? The customer wants it
            on-premise? Change the target in your build, not your code.
          </p>
          <div className={styles.buttons}>
            <Link className={clsx('button button--lg', styles.buttonPrimary)} to="/docs/guides/quickstart">
              Get started
            </Link>
            <Link className={clsx('button button--lg', styles.buttonGhost)} to="/docs/intro">
              How it works
            </Link>
          </div>
        </div>
        <div className={styles.heroVisual}>
          <HopSvg className={styles.mascot} role="img" aria-label="The Cloudhopper grasshopper jumping between clouds" />
          <div className={styles.window}>
            <div className={styles.windowBar} aria-hidden="true">
              <span /><span /><span />
              <em>HelloFunction.java</em>
            </div>
            <CodeBlock language="java" className={styles.windowCode}>{EXAMPLE}</CodeBlock>
          </div>
        </div>
      </div>
    </header>
  );
}

function Targets() {
  return (
    <section className={styles.targets} aria-labelledby="targets-title">
      <div className="container">
        <p id="targets-title" className={styles.kicker}>One codebase, four targets</p>
        <ul className={styles.targetList}>
          {TARGETS.map((t) => (
            <li key={t.profile} className={styles.target}>
              <span className={styles.targetName}>{t.name}</span>
              <code className={styles.targetProfile}>-P{t.profile}</code>
              <span className={styles.targetNote}>{t.note}</span>
            </li>
          ))}
        </ul>
        <p className={styles.targetFootnote}>
          Not every trigger exists on every target yet. The{' '}
          <Link to="/docs/platforms/generator-overview">provider overview</Link> lists
          the differences, and the build warns you about anything a target ignores.
        </p>
      </div>
    </section>
  );
}

function HowItWorks() {
  return (
    <section className={styles.section} aria-labelledby="how-title">
      <div className="container">
        <Heading as="h2" id="how-title" className={styles.sectionTitle}>
          How it works
        </Heading>
        <ol className={styles.steps}>
          {STEPS.map((s, i) => (
            <li key={s.title} className={styles.step}>
              <span className={styles.stepNumber} aria-hidden="true">{String(i + 1).padStart(2, '0')}</span>
              <Heading as="h3" className={styles.stepTitle}>{s.title}</Heading>
              <p>{s.text}</p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

function Install() {
  return (
    <section className={clsx(styles.section, styles.install)} aria-labelledby="install-title">
      <div className={clsx('container', styles.installGrid)}>
        <div>
          <Heading as="h2" id="install-title" className={styles.sectionTitle}>
            From an empty folder to a deployed function
          </Heading>
          <p className={styles.installText}>
            The archetype creates a project with an example function and a Maven
            profile per target. The quickstart walks you through building and
            deploying it.
          </p>
          <Link className={clsx('button button--lg', styles.buttonPrimary)} to="/docs/guides/quickstart">
            Open the quickstart
          </Link>
        </div>
        <div className={styles.installCode}>
          <CodeBlock language="bash">{ARCHETYPE}</CodeBlock>
        </div>
      </div>
    </section>
  );
}

function LicenseAndMaker() {
  return (
    <section className={clsx(styles.section, styles.sectionAlt)}>
      <div className={clsx('container', styles.twoCol)}>
        <div>
          <Heading as="h2" className={styles.smallTitle}>Open source, and the output is yours</Heading>
          <p>
            Dukehoff Cloudhopper is licensed under the GNU General Public License,
            version 3, with the Classpath Exception and a Generator Output
            Exception. Everything it generates for your project, such as handler
            classes, Terraform and API descriptions, you may use, modify and
            distribute under terms of your choice.
          </p>
          <Link href="https://github.com/dukehoff-gmbh/dukehoff-cloudhopper/blob/main/LICENSE-EXCEPTIONS">
            Read the exceptions
          </Link>
        </div>
        <div>
          <Heading as="h2" className={styles.smallTitle}>Built by Dukehoff GmbH</Heading>
          <p>
            Dukehoff Cloudhopper is developed by Dukehoff GmbH near Munich. If you
            want help moving Java services to serverless, between cloud providers
            or onto your own servers, talk to us.
          </p>
          <Link href="https://dukehoff.com/en/contact/">Contact Dukehoff</Link>
        </div>
      </div>
    </section>
  );
}

export default function Home() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout title="Write once, run anywhere. Revisited." description={siteConfig.customFields.description}>
      <Hero />
      <main>
        <Targets />
        <HowItWorks />
        <HomepageFeatures />
        <Install />
        <LicenseAndMaker />
      </main>
    </Layout>
  );
}
